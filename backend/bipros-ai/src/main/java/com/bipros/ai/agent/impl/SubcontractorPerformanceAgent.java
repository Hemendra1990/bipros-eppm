package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.resource.domain.model.ActivitySubContractorAssignment;
import com.bipros.resource.domain.repository.ActivitySubContractorAssignmentRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Sub-Contractor Performance agent. Deterministic {@link #gather} over the project's sub-contractor
 * assignments, surfacing the canonical Sub-Contractor KPIs from stored planned-vs-actual figures:
 *
 * <ul>
 *   <li><b>SC LCPI</b> = planned cost ÷ actual cost (≥1 on/under budget, &lt;1 overrun)</li>
 *   <li><b>Cost Variance</b> = planned cost − actual cost (+ under budget)</li>
 *   <li><b>Quantity Completion</b> = actual units ÷ planned units</li>
 * </ul>
 *
 * <p>All inputs are read straight from {@link ActivitySubContractorAssignment} (the same rows that
 * feed the DBS Section-F / Sub-Contractor tab) — no formula is re-derived. Dormant on projects with
 * no sub-contractor assignments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubcontractorPerformanceAgent extends AbstractAgent {

    private static final String KEY = "subcontractor_performance";
    private static final Duration TTL = Duration.ofDays(7);
    /** SC LCPI below this fires the cost-overrun finding. */
    private static final double LCPI_FLOOR = 0.98;
    private static final int MAX_EXAMPLES = 6;

    private final ActivitySubContractorAssignmentRepository scRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Sub-Contractor Performance";
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

        List<ActivitySubContractorAssignment> rows = scRepository.findByProjectId(projectId);
        double plannedCost = 0;
        double actualCost = 0;
        List<Over> overruns = new ArrayList<>();
        for (ActivitySubContractorAssignment a : rows) {
            double pc = dbl(a.getPlannedCost());
            double ac = dbl(a.getActualCost());
            plannedCost += pc;
            actualCost += ac;
            // Cost overrun on a started assignment (actual booked beyond the plan).
            if (ac > pc && ac > 0) {
                double lcpi = pc > 0 ? pc / ac : 0;
                double qty = dbl(a.getPlannedUnits()) > 0 ? dbl(a.getActualUnits()) / dbl(a.getPlannedUnits()) : 0;
                overruns.add(new Over(label(a), pc, ac, lcpi, qty, a.getUnit()));
            }
        }

        if (rows.isEmpty() || actualCost <= 0) {
            // No sub-contractor spend yet — nothing to judge.
            return new GatherResult(snapshot, candidates);
        }

        double lcpi = actualCost > 0 ? plannedCost / actualCost : 0;
        double variance = plannedCost - actualCost;
        snapshot.put("subContractorCount", rows.size());
        snapshot.put("plannedCost", round(plannedCost));
        snapshot.put("actualCost", round(actualCost));
        snapshot.put("scLcpi", round2(lcpi));
        snapshot.put("overrunAssignments", overruns.size());

        if (lcpi < LCPI_FLOOR || !overruns.isEmpty()) {
            candidates.add(costOverrun(projectId, plannedCost, actualCost, lcpi, variance, overruns, validUntil));
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft costOverrun(UUID projectId, double plannedCost, double actualCost, double lcpi,
                                          double variance, List<Over> overruns, Instant validUntil) {
        overruns.sort(Comparator.comparingDouble((Over o) -> o.actual - o.planned).reversed());
        double overrunPct = plannedCost > 0 ? (actualCost - plannedCost) / plannedCost * 100 : 0;
        Severity severity = (lcpi < 0.80 || overrunPct >= 15) ? Severity.HIGH
                : (lcpi < 0.95 || overrunPct >= 5) ? Severity.MEDIUM : Severity.LOW;

        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Planned SC cost", money(plannedCost)));
        ev.add(EvidenceRef.metric("Actual SC cost", money(actualCost)));
        ev.add(EvidenceRef.metric("Cost variance", money(variance)));
        ev.add(EvidenceRef.metric("SC LCPI", fmt2(lcpi)));
        for (Over o : overruns.subList(0, Math.min(MAX_EXAMPLES, overruns.size()))) {
            ev.add(EvidenceRef.metric(o.name,
                    money(o.actual) + " actual vs " + money(o.planned) + " planned (LCPI " + fmt2(o.lcpi) + ")"));
        }
        ev.add(EvidenceRef.entity("Sub-contractor report", "Open", "project", projectId,
                "/projects/" + projectId + "/dbs"));

        String worst = overruns.isEmpty() ? "sub-contract work" : overruns.get(0).name;
        return new AgentFindingDraft(
                "SUBCONTRACTOR_COST_OVERRUN", "PROJECT", severity, 0.9,
                "Sub-contractor planned vs actual cost from the resource assignments (SC LCPI = planned ÷ actual)",
                "Sub-contract spend is running over plan — SC LCPI " + fmt2(lcpi),
                "Actual sub-contractor cost (" + money(actualCost) + ") is running "
                        + (variance < 0 ? money(-variance) + " over" : "on") + " the planned "
                        + money(plannedCost) + " — an SC LCPI of " + fmt2(lcpi) + " across "
                        + overruns.size() + " over-running assignment(s); the worst is " + worst + ".",
                "Sub-contract rates or executed quantities are exceeding the committed plan, so committed work is "
                        + "being delivered less cost-efficiently than the assignments assumed.",
                "Every point of SC LCPI below 1.0 is committed budget consumed without matching output — left "
                        + "unchecked it erodes the project margin the P&L tab reports.",
                "Review the over-running sub-contract work-types with commercial, reconcile executed quantities "
                        + "against the RA bills, and re-negotiate or re-scope where the rate basis has drifted.",
                ev, Map.of("PROJECT_MANAGER", List.of(), "PLANNING_ENGINEER", List.of()), validUntil);
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private static String label(ActivitySubContractorAssignment a) {
        String n = a.getWorkTypeName() != null ? a.getWorkTypeName() : "Sub-contract work";
        return n.length() > 48 ? n.substring(0, 47) + "…" : n;
    }

    private static double dbl(BigDecimal b) {
        return b == null ? 0 : b.doubleValue();
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String money(double v) {
        return String.format(Locale.ROOT, "%,.0f", v);
    }

    private static String fmt2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private record Over(String name, double planned, double actual, double lcpi, double qtyCompletion, String unit) {
    }
}
