package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprManpowerRepository;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Labour Cost Intelligence agent. Deterministic {@link #gather} over the project's manpower resource
 * assignments and DPR output, surfacing the canonical labour-cost KPIs from stored planned-vs-actual:
 *
 * <ul>
 *   <li><b>PLC</b> = Σ planned cost ; <b>ALC</b> = Σ actual cost (manpower assignments)</li>
 *   <li><b>Manpower Cost Variance</b> = PLC − ALC ; <b>LCPI</b> = PLC ÷ ALC</li>
 *   <li><b>Cost per Unit Output</b> = Σ actual labour cost(activity) ÷ Σ DPR qty executed(activity)</li>
 * </ul>
 *
 * <p>PLC is read from {@link ResourceAssignment} planned cost (manpower assignments carry a
 * {@code manpowerRoleRateId}); ALC is the canonical Σ {@code dpr_manpower.line_cost} — the same
 * manpower actual cost the Cost summary sums, so the agent reconciles with the Costs tab. Dormant
 * when no manpower actual cost is booked yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LabourCostIntelligenceAgent extends AbstractAgent {

    private static final String KEY = "labour_cost_intelligence";
    private static final Duration TTL = Duration.ofDays(7);
    /** LCPI below this fires the overrun finding. */
    private static final double LCPI_FLOOR = 0.98;
    /** An activity's unit labour cost this many times the project mean is an outlier. */
    private static final double OUTLIER_FACTOR = 2.0;
    private static final int MAX_EXAMPLES = 6;

    private final ResourceAssignmentRepository assignmentRepository;
    private final DailyProgressReportRepository dprRepository;
    private final DprManpowerRepository dprManpowerRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Labour Cost Intelligence";
    }

    @Override
    public boolean supportsPortfolio() {
        return false;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        UUID projectId = ctx.projectId();
        ObjectNode snapshot = objectMapper.createObjectNode();
        List<AgentFindingDraft> candidates = new ArrayList<>();
        if (projectId == null) {
            return new GatherResult(snapshot, candidates);
        }

        Instant now = ctx.now() == null ? Instant.now() : ctx.now();
        Instant validUntil = now.plus(TTL);

        // PLC = Σ planned cost over manpower resource assignments (those carrying a manpowerRoleRateId).
        double plc = 0;
        for (ResourceAssignment a : assignmentRepository.findByProjectId(projectId)) {
            if (a.getManpowerRoleRateId() == null) continue;
            plc += dbl(a.getPlannedCost());
        }
        // ALC = canonical manpower actual cost = Σ dpr_manpower.line_cost over APPROVED DPRs — the same figure
        // the Cost summary / Operational-Insights "Total Manpower Cost" uses (the metrics doc: all figures use
        // approved DPRs). NOT ResourceAssignment.actualCost (~0 on the role-only model).
        double alc = dbl(dprManpowerRepository.sumLineCostByProjectApproved(projectId));

        if (alc <= 0) {
            // No actual manpower cost booked yet — nothing to judge.
            return new GatherResult(snapshot, candidates);
        }

        // Output qty + activity names from DPRs (submitted/approved).
        Map<UUID, Double> outputByActivity = new HashMap<>();
        Map<UUID, String> nameByActivity = new HashMap<>();
        for (DailyProgressReport d : dprRepository.findByProjectIdOrderByReportDateAscIdAsc(projectId)) {
            DprApprovalStatus st = d.getApprovalStatus();
            if (st != DprApprovalStatus.APPROVED) continue;   // all figures use approved DPRs (metrics doc)
            if (d.getActivityId() == null || d.getQtyExecuted() == null) continue;
            outputByActivity.merge(d.getActivityId(), d.getQtyExecuted().doubleValue(), Double::sum);
            if (d.getActivityName() != null) nameByActivity.putIfAbsent(d.getActivityId(), d.getActivityName());
        }

        double lcpi = plc > 0 ? plc / alc : 0;
        double variance = plc - alc;
        snapshot.put("plc", round(plc));
        snapshot.put("alc", round(alc));
        snapshot.put("lcpi", round(lcpi));

        // Cost per unit output per activity = canonical Σ dpr_manpower.line_cost(activity) ÷ Σ qty(activity),
        // + project weighted mean for outlier detection.
        List<UnitCost> unitCosts = new ArrayList<>();
        double totalCostWithOutput = 0;
        double totalOutput = 0;
        for (Map.Entry<UUID, Double> e : outputByActivity.entrySet()) {
            double out = e.getValue();
            if (out <= 0) continue;
            double laborCost = dbl(dprManpowerRepository.sumLineCostByProjectAndActivityApproved(projectId, e.getKey()));
            if (laborCost <= 0) continue;
            double cpu = laborCost / out;
            unitCosts.add(new UnitCost(nameFor(e.getKey(), nameByActivity), e.getKey(), laborCost, out, cpu));
            totalCostWithOutput += laborCost;
            totalOutput += out;
        }
        double meanUnitCost = totalOutput > 0 ? totalCostWithOutput / totalOutput : 0;
        List<UnitCost> outliers = new ArrayList<>();
        for (UnitCost u : unitCosts) {
            if (meanUnitCost > 0 && u.costPerUnit > meanUnitCost * OUTLIER_FACTOR) outliers.add(u);
        }
        snapshot.put("unitCostActivities", unitCosts.size());
        snapshot.put("unitCostOutliers", outliers.size());

        if (lcpi < LCPI_FLOOR || variance < 0) {
            candidates.add(costOverrun(projectId, plc, alc, lcpi, variance, validUntil));
        }
        if (!outliers.isEmpty()) {
            candidates.add(highUnitCost(projectId, outliers, meanUnitCost, validUntil));
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft costOverrun(UUID projectId, double plc, double alc, double lcpi, double variance,
                                          Instant validUntil) {
        double overrunPct = plc > 0 ? (alc - plc) / plc * 100 : 0;
        Severity severity = (lcpi < 0.80 || overrunPct >= 15) ? Severity.HIGH
                : (lcpi < 0.95 || overrunPct >= 5) ? Severity.MEDIUM : Severity.LOW;

        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.money("Planned labour cost (PLC)", BigDecimal.valueOf(plc)));
        ev.add(EvidenceRef.money("Actual labour cost (ALC)", BigDecimal.valueOf(alc)));
        ev.add(EvidenceRef.money("Manpower cost variance", BigDecimal.valueOf(variance)));
        ev.add(EvidenceRef.metric("LCPI", fmt2(lcpi)));
        // Operational Insights → Manpower KPIs, NOT the Daily Cost Report. The daily report is
        // total cost against BOQ rates for every resource; these figures are manpower-only, so it
        // is the Manpower KPI card (planned/actual manpower cost, LCPI) that matches this finding.
        ev.add(EvidenceRef.entity("Manpower KPIs", "Open", "project", projectId,
                "/projects/" + projectId + "/insights/operational"));

        return new AgentFindingDraft(
                "LABOUR_COST_OVERRUN", "PROJECT", severity, 0.9,
                "Planned vs actual labour cost from the resource assignments (LCPI = PLC ÷ ALC)",
                "Labour cost is running over plan — LCPI " + fmt2(lcpi),
                "Actual labour cost (" + money(alc) + ") is running "
                        + (variance < 0 ? money(-variance) + " over" : "on") + " the planned " + money(plc)
                        + " — a labour cost performance index (LCPI) of " + fmt2(lcpi) + ".",
                "The deployed crew is costing more than the resource plan budgeted for the work done — either more "
                        + "headcount/overtime than planned, or output lagging the manpower on site.",
                "Labour overspend flows straight into the cost variance and margin on the Costs and P&L tabs; the "
                        + "gap compounds every day it is not corrected.",
                "Reconcile deployed vs planned crews on the lagging fronts, curb overtime where output does not "
                        + "justify it, and re-check the labour rates against the plan.",
                ev, Map.of("PROJECT_MANAGER", List.of(), "SITE_MANAGER", List.of()), validUntil);
    }

    private AgentFindingDraft highUnitCost(UUID projectId, List<UnitCost> outliers, double mean, Instant validUntil) {
        outliers.sort(Comparator.comparingDouble((UnitCost u) -> u.costPerUnit).reversed());
        int n = outliers.size();
        UnitCost worst = outliers.get(0);
        double worstMult = mean > 0 ? worst.costPerUnit / mean : 0;
        Severity severity = worstMult >= 3.0 ? Severity.MEDIUM : Severity.LOW;

        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Activities above " + fmt1(OUTLIER_FACTOR) + "× mean unit cost",
                String.valueOf(n)));
        ev.add(EvidenceRef.money("Project mean unit labour cost", BigDecimal.valueOf(mean)));
        for (UnitCost u : outliers.subList(0, Math.min(MAX_EXAMPLES, n))) {
            ev.add(EvidenceRef.entity(u.name,
                    money(u.costPerUnit) + " / unit (" + money(u.laborCost) + " over " + fmt1(u.output) + " units)",
                    "activity", u.activityId,
                    u.activityId == null ? "/projects/" + projectId + "/insights/operational"
                            : "/projects/" + projectId + "/activities/" + u.activityId));
        }

        return new AgentFindingDraft(
                "HIGH_UNIT_LABOUR_COST", "PROJECT", severity, 0.75,
                "Actual labour cost per unit of output (Σ labour cost ÷ Σ qty executed) per activity",
                n + " activit" + (n == 1 ? "y is" : "ies are") + " costing well above the project's mean unit labour cost",
                "The worst, " + worst.name + ", is costing " + money(worst.costPerUnit) + " of labour per unit of "
                        + "output — about " + fmt1(worstMult) + "× the project mean of " + money(mean) + " / unit.",
                "A few activities are absorbing a disproportionate share of labour cost per unit delivered — a sign of "
                        + "over-manning, rework, or output that is lagging the crew on those fronts.",
                "High unit labour cost is where margin leaks fastest; these activities set the pace for the cost "
                        + "overrun the Costs tab will show.",
                "Review the highest unit-cost activities with the site manager — right-size the crew or clear the "
                        + "blockers (materials, access, rework) dragging output below the manpower deployed.",
                ev, Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()), validUntil);
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private static String nameFor(UUID activityId, Map<UUID, String> names) {
        String n = names.get(activityId);
        return n == null ? "activity" : (n.length() > 48 ? n.substring(0, 47) + "…" : n);
    }

    private static double dbl(BigDecimal b) {
        return b == null ? 0 : b.doubleValue();
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String money(double v) {
        return String.format(Locale.ROOT, "%,.0f", v);
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String fmt2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private record UnitCost(String name, UUID activityId, double laborCost, double output, double costPerUnit) {
    }
}
