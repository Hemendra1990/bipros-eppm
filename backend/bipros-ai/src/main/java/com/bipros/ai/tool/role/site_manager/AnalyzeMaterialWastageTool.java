package com.bipros.ai.tool.role.site_manager;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ProjectScopedTool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.resource.domain.model.MaterialReconciliation;
import com.bipros.resource.domain.repository.MaterialReconciliationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reports material wastage percentage per material (resource) and WBS location from
 * {@link MaterialReconciliation} entries over a date range.
 *
 * <p>Wastage % is computed as {@code wastage / consumed * 100} where:
 * <ul>
 *   <li>{@code consumed} — quantity productively used</li>
 *   <li>{@code wastage} — quantity wasted (tracked separately in the reconciliation entry)</li>
 * </ul>
 *
 * <p>Rows are grouped by {@code (resourceId, wbsNodeId)} and ordered by wastage % descending
 * so the worst offenders appear first.
 *
 * <p>The date range is applied by filtering the {@code period} field (YYYY-MM format) against
 * the months that overlap the requested {@code from}–{@code to} date range.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeMaterialWastageTool extends ProjectScopedTool {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MaterialReconciliationRepository reconciliationRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "analyze_material_wastage";
    }

    @Override
    public String description() {
        return "Wastage % per material/location from MaterialReconciliation entries, computed as "
                + "wastage / consumed * 100. Returns rows ordered by wastage % desc. "
                + "Defaults to last 30 days. Grouped by resource (material) and WBS location.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("from", objectMapper.createObjectNode()
                .put("type", "string")
                .put("format", "date")
                .put("description", "Start date (inclusive). Defaults to 30 days before 'to'."));
        props.set("to", objectMapper.createObjectNode()
                .put("type", "string")
                .put("format", "date")
                .put("description", "End date (inclusive). Defaults to today."));
        schema.set("properties", props);
        return schema;
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("SITE_MANAGER", "PROJECT_MANAGER", "PROJECT_ENGINEER");
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        if (ctx.projectId() == null) {
            return ToolResult.error("Pick a project first — material wastage analysis is per-project.");
        }

        LocalDate to = parse(input.path("to").asText(null), LocalDate.now());
        LocalDate from = parse(input.path("from").asText(null), to.minusDays(30));

        // Convert date range to period strings (YYYY-MM) for filtering
        String fromPeriod = from.format(PERIOD_FMT);
        String toPeriod = to.format(PERIOD_FMT);

        List<MaterialReconciliation> all;
        try {
            all = reconciliationRepository.findByProjectId(ctx.projectId());
        } catch (Exception e) {
            log.warn("analyze_material_wastage: repository query failed: {}", e.getMessage());
            return dataUnavailable(
                    "MaterialReconciliation data is not accessible for this project.",
                    "Ensure Material Reconciliation entries are being submitted for this project.",
                    "query_dpr (raw daily progress with material lines)");
        }

        // Filter by period range (YYYY-MM lexicographic comparison is correct for this format)
        List<MaterialReconciliation> filtered = all.stream()
                .filter(r -> r.getPeriod() != null
                        && r.getPeriod().compareTo(fromPeriod) >= 0
                        && r.getPeriod().compareTo(toPeriod) <= 0)
                .toList();

        if (filtered.isEmpty()) {
            return dataUnavailable(
                    "No material reconciliation rows for project " + ctx.projectId()
                            + " between " + from + " and " + to + ".",
                    "Confirm Material Reconciliation entries are being submitted for this period.",
                    "query_dpr to inspect raw DPR records with material consumption");
        }

        // Group by (resourceId, wbsNodeId) and accumulate consumed + wastage
        record GroupKey(UUID resourceId, UUID wbsNodeId) {}

        Map<GroupKey, double[]> grouped = new HashMap<>();
        for (MaterialReconciliation r : filtered) {
            GroupKey key = new GroupKey(r.getResourceId(), r.getWbsNodeId());
            grouped.compute(key, (k, acc) -> {
                if (acc == null) acc = new double[]{0.0, 0.0};
                acc[0] += r.getConsumed() != null ? r.getConsumed() : 0.0;  // consumed
                acc[1] += r.getWastage() != null ? r.getWastage() : 0.0;    // wastage
                return acc;
            });
        }

        // Build result rows ordered by wastage_pct desc
        record WastageRow(UUID resourceId, UUID wbsNodeId, double consumed, double wastage, double wastagePct) {}

        List<WastageRow> rows = grouped.entrySet().stream()
                .map(e -> {
                    double consumed = e.getValue()[0];
                    double wastage = e.getValue()[1];
                    double wastagePct = consumed > 0.0 ? (wastage / consumed) * 100.0 : 0.0;
                    return new WastageRow(e.getKey().resourceId(), e.getKey().wbsNodeId(),
                            consumed, wastage, wastagePct);
                })
                .sorted(Comparator.comparingDouble(WastageRow::wastagePct).reversed())
                .toList();

        ArrayNode arr = objectMapper.createArrayNode();
        for (WastageRow row : rows) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("resource_id", row.resourceId().toString());
            o.put("wbs_node_id", row.wbsNodeId() != null ? row.wbsNodeId().toString() : "");
            o.put("consumed", Math.round(row.consumed() * 100.0) / 100.0);
            o.put("wastage", Math.round(row.wastage() * 100.0) / 100.0);
            o.put("wastage_pct", Math.round(row.wastagePct() * 10.0) / 10.0);
            arr.add(o);
        }

        return ToolResult.table(
                "Material wastage from " + from + " to " + to + " — " + rows.size() + " material/location group(s).",
                arr,
                new String[]{"resource_id", "wbs_node_id", "consumed", "wastage", "wastage_pct"}
        );
    }

    private ToolResult dataUnavailable(String reason, String whatNeeded, String closest) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status", "data_unavailable");
        payload.put("reason", reason);
        payload.put("what_would_be_needed", whatNeeded);
        payload.put("closest_available", closest);
        return ToolResult.ok("Data not yet captured: " + reason, payload);
    }

    private LocalDate parse(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalDate.parse(raw);
        } catch (Exception e) {
            return fallback;
        }
    }
}
