package com.bipros.ai.tool.role.bim_coordinator;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ProjectScopedTool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprEquipmentRepository;
import com.bipros.project.domain.repository.DprManpowerRepository;
import com.bipros.project.domain.repository.DprMaterialRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Scores the data completeness of every DPR in a project's last 30 days across
 * a canonical set of critical fields. Results are aggregated per DPR and sorted
 * by completeness ascending so the most incomplete records surface first.
 *
 * <p><strong>Critical fields checked:</strong>
 * <ul>
 *   <li>{@code weatherCondition}       — environmental context</li>
 *   <li>{@code supervisorName}         — accountability chain</li>
 *   <li>{@code safetyObservation}      — HSE requirement</li>
 *   <li>{@code approvalStatus}         — workflow sign-off</li>
 *   <li>{@code remarks}                — field notes</li>
 *   <li>{@code contractorName}         — commercial traceability</li>
 *   <li>{@code shift}                  — time-of-work context</li>
 *   <li>{@code hasManpowerLines}       — labour usage recorded</li>
 *   <li>{@code hasEquipmentLines}      — equipment usage recorded</li>
 *   <li>{@code hasMaterialLines}       — material consumption recorded</li>
 * </ul>
 *
 * <p>Completeness % = (populated / total critical fields) × 100, rounded to 1 d.p.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditDprDataQualityTool extends ProjectScopedTool {

    /**
     * Canonical set of critical field names for DPR completeness scoring.
     * Extend this set to add additional checks — no other code change required.
     */
    static final Set<String> CRITICAL_FIELDS = Set.of(
            "weatherCondition",
            "supervisorName",
            "safetyObservation",
            "approvalStatus",
            "remarks",
            "contractorName",
            "shift",
            "hasManpowerLines",
            "hasEquipmentLines",
            "hasMaterialLines"
    );

    private static final int WINDOW_DAYS = 30;

    private final DailyProgressReportRepository dprRepository;
    private final DprManpowerRepository         manpowerRepository;
    private final DprEquipmentRepository        equipmentRepository;
    private final DprMaterialRepository         materialRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "audit_dpr_data_quality";
    }

    @Override
    public String description() {
        return "For each DPR in the project's last 30 days, score completeness across critical "
                + "fields (weatherCondition, supervisorName, safetyObservation, approvalStatus, "
                + "remarks, contractorName, shift, hasManpowerLines, hasEquipmentLines, "
                + "hasMaterialLines). Returns one row per DPR ordered by completeness_pct ASC "
                + "so the most incomplete records appear first. Also includes summary fields: "
                + "total_dprs and avg_completeness_pct. "
                + "Use to prioritise which DPRs need corrective data entry before BIM submission.";
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
        return Set.of("BIM_DATA_COORDINATOR", "PROJECT_MANAGER", "PORTFOLIO_MANAGER");
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        if (ctx.projectId() == null) {
            return ToolResult.error("audit_dpr_data_quality requires a project in scope.");
        }

        UUID projectId = ctx.projectId();
        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(WINDOW_DAYS);

        List<DailyProgressReport> dprs;
        try {
            dprs = dprRepository.findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(
                    projectId, from, to);
        } catch (Exception e) {
            log.warn("audit_dpr_data_quality: repository query failed for project {}: {}",
                    projectId, e.getMessage());
            return dataUnavailable(
                    "DPR data is not accessible for this project.",
                    "Ensure the project schema is migrated and DPRs have been submitted.");
        }

        if (dprs.isEmpty()) {
            return dataUnavailable(
                    "No DPRs found for project " + projectId
                            + " in the last " + WINDOW_DAYS + " days.",
                    "Submit at least one Daily Progress Report for the project.");
        }

        // Collect all DPR IDs for bulk child-row queries
        List<UUID> dprIds = dprs.stream().map(DailyProgressReport::getId).toList();

        // Bulk-fetch child lines to determine has* flags per DPR — avoids N+1 queries
        Set<UUID> dprIdsWithManpower = manpowerRepository.findByDprIdIn(dprIds)
                .stream().map(m -> m.getDprId()).collect(Collectors.toSet());
        Set<UUID> dprIdsWithEquipment = equipmentRepository.findByDprIdIn(dprIds)
                .stream().map(e -> e.getDprId()).collect(Collectors.toSet());
        Set<UUID> dprIdsWithMaterial = materialRepository.findByDprIdIn(dprIds)
                .stream().map(m -> m.getDprId()).collect(Collectors.toSet());

        // Score each DPR
        record ScoredDpr(
                UUID id,
                LocalDate reportDate,
                double completenessPct,
                List<String> missingFields
        ) {}

        List<ScoredDpr> scored = new ArrayList<>(dprs.size());
        for (DailyProgressReport dpr : dprs) {
            UUID dprId = dpr.getId();
            List<String> missing = computeMissingFields(
                    dpr,
                    dprIdsWithManpower.contains(dprId),
                    dprIdsWithEquipment.contains(dprId),
                    dprIdsWithMaterial.contains(dprId));

            int populated = CRITICAL_FIELDS.size() - missing.size();
            double pct = Math.round(
                    1000.0 * populated / CRITICAL_FIELDS.size()) / 10.0;  // 1 d.p.

            scored.add(new ScoredDpr(dprId, dpr.getReportDate(), pct, missing));
        }

        // Sort by completeness ASC (worst first)
        scored.sort(Comparator.comparingDouble(ScoredDpr::completenessPct));

        // Build output rows
        ArrayNode rows = objectMapper.createArrayNode();
        for (ScoredDpr s : scored) {
            ObjectNode row = objectMapper.createObjectNode();
            row.put("dpr_id",           s.id().toString());
            row.put("report_date",      s.reportDate().toString());
            row.put("completeness_pct", s.completenessPct());

            ArrayNode missingArr = objectMapper.createArrayNode();
            s.missingFields().stream().sorted().forEach(missingArr::add);
            row.set("missing_fields", missingArr);

            rows.add(row);
        }

        // Summary
        double avgPct = scored.stream()
                .mapToDouble(ScoredDpr::completenessPct)
                .average()
                .orElse(0.0);
        avgPct = Math.round(avgPct * 10.0) / 10.0;

        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("total_dprs",           scored.size());
        wrapper.put("avg_completeness_pct", avgPct);
        wrapper.set("rows",    rows);
        ArrayNode cols = objectMapper.createArrayNode();
        for (String c : new String[]{"dpr_id", "report_date", "completeness_pct", "missing_fields"}) {
            cols.add(c);
        }
        wrapper.set("columns", cols);

        String summary = String.format(
                "DPR data quality audit for project %s (%s to %s): %d DPR(s), "
                        + "avg completeness %.1f%%. Sorted worst-first.",
                projectId, from, to, scored.size(), avgPct);

        return ToolResult.ok(summary, wrapper);
    }

    // -----------------------------------------------------------------------

    /**
     * Returns the list of critical field names that are absent or blank in {@code dpr}.
     */
    private static List<String> computeMissingFields(
            DailyProgressReport dpr,
            boolean hasManpower,
            boolean hasEquipment,
            boolean hasMaterial) {

        List<String> missing = new ArrayList<>();

        if (isBlank(dpr.getWeatherCondition()))  missing.add("weatherCondition");
        if (isBlank(dpr.getSupervisorName()))     missing.add("supervisorName");
        if (isBlank(dpr.getSafetyObservation()))  missing.add("safetyObservation");
        if (dpr.getApprovalStatus() == null)      missing.add("approvalStatus");
        if (isBlank(dpr.getRemarks()))            missing.add("remarks");
        if (isBlank(dpr.getContractorName()))     missing.add("contractorName");
        if (dpr.getShift() == null)               missing.add("shift");
        if (!hasManpower)                         missing.add("hasManpowerLines");
        if (!hasEquipment)                        missing.add("hasEquipmentLines");
        if (!hasMaterial)                         missing.add("hasMaterialLines");

        return missing;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private ToolResult dataUnavailable(String reason, String whatNeeded) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status",               "data_unavailable");
        payload.put("reason",               reason);
        payload.put("what_would_be_needed", whatNeeded);
        return ToolResult.ok("Data not yet captured: " + reason, payload);
    }
}
