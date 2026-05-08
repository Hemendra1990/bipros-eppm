package com.bipros.ai.tool.role.project_manager;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ProjectScopedTool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.resource.domain.model.MaterialIssue;
import com.bipros.resource.domain.model.MaterialStock;
import com.bipros.resource.domain.repository.MaterialIssueRepository;
import com.bipros.resource.domain.repository.MaterialStockRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * For high-value materials (approximated by highest total quantity consumed in the last 7 days),
 * computes the daily burn rate and compares it against current stock to determine how many days
 * of supply remain. Materials with {@code days_remaining < 5} are flagged as {@code at_risk}.
 *
 * <p>Data sources:
 * <ul>
 *   <li>{@link MaterialIssueRepository} — issue records with exact {@code issueDate} and
 *       {@code quantity} per material, used to compute last-7-day consumption per material.
 *       {@code daily_burn = sum(quantity over last 7 days) / 7}</li>
 *   <li>{@link MaterialStockRepository} — aggregate stock state per (project, material),
 *       providing {@code currentStock} for the days-remaining calculation.</li>
 * </ul>
 *
 * <p>"High-value" approximation: the tool limits output to the top 50 materials by total
 * quantity consumed in the 7-day window (highest qty = highest value proxy when unit cost
 * is not stored on the {@code Material} catalogue entry). This is documented in the tool
 * description so the LLM can relay the approximation to the user.
 *
 * <p>Formula:
 * <pre>
 *   daily_burn     = sum(issued_qty, last 7 days) / 7
 *   days_remaining = current_stock / daily_burn   (null when daily_burn == 0)
 *   at_risk        = days_remaining != null &amp;&amp; days_remaining &lt; 5
 * </pre>
 *
 * <p>Results are sorted by {@code days_remaining} ASC NULLS LAST (most urgent first).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeMaterialBurnRateTool extends ProjectScopedTool {

    private static final int WINDOW_DAYS = 7;
    private static final double AT_RISK_THRESHOLD = 5.0;
    private static final int TOP_N = 50;

    private final MaterialIssueRepository issueRepository;
    private final MaterialStockRepository stockRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "analyze_material_burn_rate";
    }

    @Override
    public String description() {
        return "For high-value materials (top 50 by quantity consumed, proxy for high-value since "
                + "unit cost is not stored in the material catalogue), compute daily burn rate over "
                + "the last 7 days vs current stock. Flag materials with days_remaining < 5 as "
                + "at_risk. Results sorted by days_remaining ASC (most urgent first). "
                + "Uses MaterialIssue records for burn rate and MaterialStock for current stock.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("as_of", objectMapper.createObjectNode()
                .put("type", "string")
                .put("format", "date")
                .put("description", "Reference date for 'last 7 days' window. Defaults to today."));
        schema.set("properties", props);
        return schema;
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("PROJECT_MANAGER", "PORTFOLIO_MANAGER", "COST_CONTROLLER", "RESOURCE_MANAGER");
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        if (ctx.projectId() == null) {
            return ToolResult.error("Pick a project first — material burn rate analysis is per-project.");
        }

        LocalDate asOf = parse(input.path("as_of").asText(null), LocalDate.now());
        LocalDate windowStart = asOf.minusDays(WINDOW_DAYS - 1); // inclusive 7-day window

        // 1. Pull issue records for the last 7 days
        List<MaterialIssue> issues;
        try {
            issues = issueRepository.findByProjectIdAndIssueDateBetween(
                    ctx.projectId(), windowStart, asOf);
        } catch (Exception e) {
            log.warn("analyze_material_burn_rate: issue repo query failed: {}", e.getMessage());
            return dataUnavailable(
                    "MaterialIssue data is not accessible for this project.",
                    "Ensure Material Issue (challan) records are being submitted for this project.",
                    "check_stockpile_vs_plan (stock vs assignment demand)");
        }

        // 2. Pull current stock for all materials in this project
        List<MaterialStock> stocks;
        try {
            stocks = stockRepository.findByProjectId(ctx.projectId());
        } catch (Exception e) {
            log.warn("analyze_material_burn_rate: stock repo query failed: {}", e.getMessage());
            return dataUnavailable(
                    "MaterialStock data is not accessible for this project.",
                    "Ensure GRN and Issue records have been submitted to maintain stock ledger.",
                    "check_stockpile_vs_plan (stock vs assignment demand)");
        }

        // Both empty → data unavailable
        if (issues.isEmpty() && stocks.isEmpty()) {
            return dataUnavailable(
                    "No material issue records or stock data for project " + ctx.projectId()
                            + " over the last " + WINDOW_DAYS + " days (ending " + asOf + ").",
                    "Submit Material Issue (challan) records and ensure the stock register is "
                            + "populated for this project.",
                    "check_stockpile_vs_plan (stock vs planned demand from resource assignments)");
        }

        // 3. Aggregate issued quantity per materialId over the 7-day window
        Map<UUID, Double> issued7d = new HashMap<>();
        for (MaterialIssue issue : issues) {
            UUID mid = issue.getMaterialId();
            double qty = issue.getQuantity() != null ? issue.getQuantity().doubleValue() : 0.0;
            issued7d.merge(mid, qty, Double::sum);
        }

        // 4. Build stock lookup: materialId → currentStock
        Map<UUID, Double> stockMap = new HashMap<>();
        for (MaterialStock s : stocks) {
            if (s.getMaterialId() != null && s.getCurrentStock() != null) {
                stockMap.put(s.getMaterialId(), s.getCurrentStock().doubleValue());
            }
        }

        // 5. Collect all materialIds from either source
        Set<UUID> allMaterials = new java.util.HashSet<>();
        allMaterials.addAll(issued7d.keySet());
        allMaterials.addAll(stockMap.keySet());

        if (allMaterials.isEmpty()) {
            return dataUnavailable(
                    "No materials found for project " + ctx.projectId() + ".",
                    "Submit Material Issue records and GRN entries to populate stock data.",
                    "check_stockpile_vs_plan");
        }

        // 6. Compute burn rate row per material; limit to top-N by total consumed (high-value proxy)
        record BurnRow(UUID materialId, double totalConsumed7d, double dailyBurn,
                       double currentStock, Double daysRemaining, boolean atRisk) {}

        List<BurnRow> rows = allMaterials.stream()
                .map(mid -> {
                    double total7d = issued7d.getOrDefault(mid, 0.0);
                    double dailyBurn = total7d / WINDOW_DAYS;
                    double currentStock = stockMap.getOrDefault(mid, 0.0);
                    Double daysRemaining = dailyBurn > 0.0 ? currentStock / dailyBurn : null;
                    boolean atRisk = daysRemaining != null && daysRemaining < AT_RISK_THRESHOLD;
                    return new BurnRow(mid, total7d, dailyBurn, currentStock, daysRemaining, atRisk);
                })
                // Limit to top 50 by total consumed (high-value proxy)
                .sorted(Comparator.comparingDouble(BurnRow::totalConsumed7d).reversed())
                .limit(TOP_N)
                // Then re-sort by days_remaining ASC NULLS LAST for final output
                .sorted(Comparator.comparing(
                        BurnRow::daysRemaining,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        // 7. Build JSON array
        ArrayNode arr = objectMapper.createArrayNode();
        for (BurnRow row : rows) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("material_id", row.materialId().toString());
            o.put("daily_burn", round2(row.dailyBurn()));
            o.put("current_stock", round2(row.currentStock()));
            if (row.daysRemaining() != null) {
                o.put("days_remaining", round2(row.daysRemaining()));
            } else {
                o.putNull("days_remaining");
            }
            o.put("at_risk", row.atRisk());
            arr.add(o);
        }

        long atRiskCount = rows.stream().filter(BurnRow::atRisk).count();
        return ToolResult.table(
                "Material burn rate (last " + WINDOW_DAYS + " days ending " + asOf + ") — "
                        + rows.size() + " material(s), " + atRiskCount + " at-risk (days_remaining < 5). "
                        + "High-value approximation: top " + TOP_N + " by qty consumed (unit cost "
                        + "not stored in material catalogue).",
                arr,
                new String[]{"material_id", "daily_burn", "current_stock", "days_remaining", "at_risk"}
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

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
