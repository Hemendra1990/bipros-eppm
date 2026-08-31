package com.bipros.ai.tool.role.site_manager;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ProjectScopedTool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.resource.domain.model.MaterialStock;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.MaterialStockRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Compares current material stock against upcoming planned demand for the lookahead window
 * ({@code [today, today + lookahead_days]}), surfacing materials at risk of running out.
 *
 * <p>Stock source: {@link com.bipros.resource.domain.model.MaterialStock} — one row per
 * {@code (projectId, materialId)}, field {@code currentStock}.
 *
 * <p>Demand source: {@link com.bipros.resource.domain.model.ResourceAssignment} — rows whose
 * {@code plannedStartDate} falls within the lookahead window and whose {@code resourceId} matches
 * a known material (i.e. there is a corresponding MaterialStock row). Demand is the sum of
 * {@code plannedUnits} per material.
 *
 * <p>Per-material output row keys:
 * <ul>
 *   <li>{@code material_id} — UUID of the material resource</li>
 *   <li>{@code current_stock} — on-hand quantity from MaterialStock</li>
 *   <li>{@code lookahead_demand} — sum of plannedUnits from ResourceAssignments in the window</li>
 *   <li>{@code stock_to_need_ratio} — {@code currentStock / lookaheadDemand} (null → ∞ represented as 99.0)</li>
 *   <li>{@code at_risk} — {@code true} when ratio < 1.0</li>
 * </ul>
 *
 * <p>Rows are ordered by {@code stock_to_need_ratio} ascending so the most critical materials
 * appear first.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckStockpileVsPlanTool extends ProjectScopedTool {

    private final MaterialStockRepository stockRepository;
    private final ResourceAssignmentRepository assignmentRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "check_stockpile_vs_plan";
    }

    @Override
    public String description() {
        return "Compare current material stock against lookahead-window planned demand, surfacing "
                + "materials at risk of running out. Reads MaterialStock (current on-hand quantity) "
                + "and ResourceAssignment (planned units scheduled within the lookahead window). "
                + "Returns per-material rows with current_stock, lookahead_demand, "
                + "stock_to_need_ratio, and at_risk flag. Default lookahead is 3 days.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("lookahead_days", objectMapper.createObjectNode()
                .put("type", "number")
                .put("description", "Number of days ahead to check planned demand. Defaults to 3."));
        schema.set("properties", props);
        return schema;
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("SITE_MANAGER", "PROJECT_MANAGER", "RESOURCE_MANAGER");
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        if (ctx.projectId() == null) {
            return ToolResult.error("Pick a project first — stockpile check is per-project.");
        }

        int lookaheadDays = input.path("lookahead_days").asInt(3);
        if (lookaheadDays < 1) lookaheadDays = 3;

        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(lookaheadDays);
        UUID projectId = ctx.projectId();

        // --- Load current stock per material ---
        List<MaterialStock> stocks;
        try {
            stocks = stockRepository.findByProjectId(projectId);
        } catch (Exception e) {
            log.warn("check_stockpile_vs_plan: stock query failed: {}", e.getMessage());
            return dataUnavailable(
                    "MaterialStock data is not accessible for project " + projectId + ".",
                    "Ensure GRN (Goods Receipt Note) entries are recorded so MaterialStock rows are created.",
                    "analyze_material_wastage");
        }

        if (stocks.isEmpty()) {
            return dataUnavailable(
                    "No MaterialStock rows found for project " + projectId
                            + ". Stock register has not been populated yet.",
                    "Record at least one Goods Receipt (GRN) for this project to initialise stock.",
                    "analyze_material_wastage");
        }

        // Build a lookup: materialId → currentStock
        Map<UUID, BigDecimal> stockByMaterial = new HashMap<>();
        for (MaterialStock s : stocks) {
            if (s.getMaterialId() != null && s.getCurrentStock() != null) {
                stockByMaterial.put(s.getMaterialId(), s.getCurrentStock());
            }
        }

        // --- Load resource assignments for the project, filter to lookahead window ---
        List<ResourceAssignment> assignments;
        try {
            assignments = assignmentRepository.findByProjectId(projectId);
        } catch (Exception e) {
            log.warn("check_stockpile_vs_plan: assignment query failed: {}", e.getMessage());
            return dataUnavailable(
                    "ResourceAssignment data is not accessible for project " + projectId + ".",
                    "Ensure activities have resource assignments with planned start/finish dates.",
                    "analyze_material_wastage");
        }

        // Sum planned units per material for assignments whose start date falls in the window.
        // Only count assignments whose resourceId matches a known material (stock register entry).
        Map<UUID, Double> demandByMaterial = new HashMap<>();
        final int effectiveLookahead = lookaheadDays;
        for (ResourceAssignment ra : assignments) {
            if (ra.getResourceId() == null) continue;
            if (!stockByMaterial.containsKey(ra.getResourceId())) continue;  // not a stocked material
            LocalDate start = ra.getPlannedStartDate();
            if (start == null) continue;
            // Window: [today, today + lookaheadDays] inclusive
            if (!start.isBefore(today) && !start.isAfter(today.plusDays(effectiveLookahead))) {
                double units = ra.getPlannedUnits() != null ? ra.getPlannedUnits() : 0.0;
                demandByMaterial.merge(ra.getResourceId(), units, Double::sum);
            }
        }

        // --- Build result rows for all stocked materials ---
        record StockRow(UUID materialId, double currentStock, double demand, double ratio) {}

        List<StockRow> rows = stockByMaterial.entrySet().stream()
                .map(e -> {
                    UUID matId = e.getKey();
                    double stock = e.getValue().doubleValue();
                    double demand = demandByMaterial.getOrDefault(matId, 0.0);
                    double ratio = demand > 0.0 ? stock / demand : 99.0;  // 99 = effectively infinite
                    return new StockRow(matId, stock, demand, ratio);
                })
                .sorted((a, b) -> Double.compare(a.ratio(), b.ratio()))
                .toList();

        ArrayNode arr = objectMapper.createArrayNode();
        for (StockRow row : rows) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("material_id", row.materialId().toString());
            o.put("current_stock", round(row.currentStock()));
            o.put("lookahead_demand", round(row.demand()));
            o.put("stock_to_need_ratio", round(row.ratio()));
            o.put("at_risk", row.ratio() < 1.0);
            arr.add(o);
        }

        long atRiskCount = rows.stream().filter(r -> r.ratio() < 1.0).count();
        String summary = "Stockpile vs plan (next " + lookaheadDays + " days): "
                + rows.size() + " material(s) checked, "
                + atRiskCount + " at risk (stock < demand).";

        return ToolResult.table(
                summary,
                arr,
                new String[]{"material_id", "current_stock", "lookahead_demand", "stock_to_need_ratio", "at_risk"}
        );
    }

    private double round(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    private ToolResult dataUnavailable(String reason, String whatNeeded, String closest) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status", "data_unavailable");
        payload.put("reason", reason);
        payload.put("what_would_be_needed", whatNeeded);
        payload.put("closest_available", closest);
        return ToolResult.ok("Data not yet captured: " + reason, payload);
    }
}
