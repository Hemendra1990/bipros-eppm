package com.bipros.ai.tool.role.project_engineer;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ProjectScopedTool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.resource.domain.model.MaterialBoqLink;
import com.bipros.resource.domain.model.MaterialReconciliation;
import com.bipros.resource.domain.repository.MaterialBoqLinkRepository;
import com.bipros.resource.domain.repository.MaterialReconciliationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Compares actual material consumption (from {@link MaterialReconciliation}) against the
 * design (BOQ) quantity (from {@link BoqItem}) per BOQ item.
 *
 * <p>The join path is:
 * <ol>
 *   <li>{@code MaterialReconciliation.resourceId} → aggregate {@code consumed} per material</li>
 *   <li>{@code MaterialBoqLink}: maps {@code materialId} → {@code boqItemId}</li>
 *   <li>{@code BoqItem}: provides {@code boqQty} (design quantity)</li>
 * </ol>
 *
 * <p>Variance % = {@code (actual - design) / design × 100}. Rows are sorted by
 * absolute variance % descending — the biggest over/under-runs appear first.
 *
 * <p>If no {@link MaterialReconciliation} data exists for the project, the tool returns a
 * {@code data_unavailable} payload rather than silently returning zero rows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeYieldVarianceTool extends ProjectScopedTool {

    private final MaterialReconciliationRepository reconciliationRepository;
    private final MaterialBoqLinkRepository boqLinkRepository;
    private final BoqItemRepository boqItemRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "analyze_yield_variance";
    }

    @Override
    public String description() {
        return "Compare actual material consumed vs design (BOQ) quantity per BOQ item. "
                + "Variance % = (actual - design) / design * 100. "
                + "Rows are sorted by absolute variance % descending so the biggest "
                + "over/under-runs appear first. "
                + "Output columns: boq_item_id, material_id, design_quantity, actual_quantity, variance_pct.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", objectMapper.createObjectNode());
        return schema;
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("PROJECT_ENGINEER", "PROJECT_MANAGER");
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        if (ctx.projectId() == null) {
            return ToolResult.error("Pick a project first — yield variance analysis is per-project.");
        }
        UUID projectId = ctx.projectId();

        // Step 1: load all material reconciliation entries for this project
        List<MaterialReconciliation> allRec;
        try {
            allRec = reconciliationRepository.findByProjectId(projectId);
        } catch (Exception e) {
            log.warn("analyze_yield_variance: reconciliation query failed: {}", e.getMessage());
            return dataUnavailable(
                    "MaterialReconciliation data is not accessible for this project.",
                    "Ensure Material Reconciliation entries are being submitted for this project.",
                    "analyze_material_wastage");
        }

        if (allRec.isEmpty()) {
            return dataUnavailable(
                    "No material reconciliation rows found for project " + projectId + ".",
                    "Material Reconciliation entries must be submitted (via DPR or the Material "
                            + "Reconciliation screen) before yield variance can be calculated.",
                    "analyze_material_wastage");
        }

        // Step 2: aggregate total consumed per material (resourceId)
        Map<UUID, Double> consumedByMaterial = new HashMap<>();
        for (MaterialReconciliation rec : allRec) {
            consumedByMaterial.merge(
                    rec.getResourceId(),
                    rec.getConsumed() != null ? rec.getConsumed() : 0.0,
                    Double::sum);
        }

        // Step 3: for each material, find its BOQ links and resolve design quantities
        record YieldRow(UUID boqItemId, UUID materialId,
                        double designQty, double actualQty, double variancePct) {}

        List<YieldRow> rows = new ArrayList<>();

        for (Map.Entry<UUID, Double> entry : consumedByMaterial.entrySet()) {
            UUID materialId = entry.getKey();
            double actualConsumed = entry.getValue();

            List<MaterialBoqLink> links = boqLinkRepository.findByMaterialId(materialId);
            if (links.isEmpty()) {
                // Material has no BOQ links — skip; include as "unlinked" would distort the report
                continue;
            }

            for (MaterialBoqLink link : links) {
                UUID boqItemId = link.getBoqItemId();
                Optional<BoqItem> boqItemOpt = boqItemRepository.findById(boqItemId);
                if (boqItemOpt.isEmpty()) {
                    log.debug("analyze_yield_variance: boq_item {} not found, skipping", boqItemId);
                    continue;
                }
                BoqItem boqItem = boqItemOpt.get();
                BigDecimal boqQty = boqItem.getBoqQty();
                if (boqQty == null || boqQty.compareTo(BigDecimal.ZERO) == 0) {
                    // Design quantity is zero — cannot compute meaningful variance
                    continue;
                }

                double designQty = boqQty.doubleValue();
                double variancePct = (actualConsumed - designQty) / designQty * 100.0;
                rows.add(new YieldRow(boqItemId, materialId, designQty, actualConsumed, variancePct));
            }
        }

        if (rows.isEmpty()) {
            return dataUnavailable(
                    "No BOQ items are linked to materials for project " + projectId + ". "
                            + "Yield variance requires materials to be associated with BOQ items "
                            + "(via the 'Applicable BOQ Items' field on the Material master).",
                    "Link at least one material to a BOQ item via Material master (Screen 09a), "
                            + "then submit Material Reconciliation entries.",
                    "analyze_material_wastage");
        }

        // Step 4: sort by |variance_pct| descending
        rows.sort(Comparator.comparingDouble((YieldRow r) -> Math.abs(r.variancePct())).reversed());

        // Step 5: build result
        ArrayNode arr = objectMapper.createArrayNode();
        for (YieldRow row : rows) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("boq_item_id", row.boqItemId().toString());
            o.put("material_id", row.materialId().toString());
            o.put("design_quantity", round2(row.designQty()));
            o.put("actual_quantity", round2(row.actualQty()));
            o.put("variance_pct", round1(row.variancePct()));
            arr.add(o);
        }

        return ToolResult.table(
                "Yield variance for project " + projectId + " — " + rows.size() + " BOQ-material pair(s).",
                arr,
                new String[]{"boq_item_id", "material_id", "design_quantity", "actual_quantity", "variance_pct"});
    }

    // -------------------------------------------------------------------------

    private ToolResult dataUnavailable(String reason, String whatNeeded, String closest) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status", "data_unavailable");
        payload.put("reason", reason);
        payload.put("what_would_be_needed", whatNeeded);
        payload.put("closest_available", closest);
        return ToolResult.ok("Data not yet captured: " + reason, payload);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
