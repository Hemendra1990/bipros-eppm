package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.support.CapacityUtilizationProvider;
import com.bipros.ai.agent.support.CapacityUtilizationProvider.RoleEfficiency;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Capacity Utilisation agent. Surfaces two distinct, canonical capacity signals — project-to-date, at
 * the resource-role / resource-type level (the Productivity agent covers per-activity output-vs-norm;
 * the Field agent covers the live site snapshot):
 *
 * <ul>
 *   <li><b>Efficiency &amp; cost overrun</b> — output vs the productivity norm per resource-day. Read
 *       EXACTLY from the canonical Capacity Util. computation via {@link CapacityUtilizationProvider}
 *       (a Dependency-Inversion port; the heavy allocator lives in the unreachable {@code
 *       bipros-reporting} module, so the agent never re-derives — and never drifts from — it).</li>
 *   <li><b>Deployment utilisation</b> — {@code deployed nos ÷ planned nos} (Operational Insights
 *       "Resource utilisation"), computed here from the DPR ledger + resource plan. Flags severe
 *       under-deployment.</li>
 * </ul>
 *
 * <p>All money is currency-neutral (relabelled per project on the frontend).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CapacityUtilisationAgent extends AbstractAgent {

    private static final String KEY = "capacity_utilisation";
    private static final Duration TTL = Duration.ofDays(7);

    /** A role needs at least this many deployed resource-days before efficiency is judged. */
    private static final double MIN_DAYS = 10.0;
    /** A role cost overrun (OMR-neutral) at or above this is worth flagging. */
    private static final double COST_OVERRUN_MIN = 2000.0;
    /** Efficiency below this (% of norm) is a severe under-performance regardless of cost. */
    private static final double SEVERE_INEFFICIENCY_PCT = 70.0;
    /** Deployment below this fraction of the planned strength is under-deployment. */
    private static final double UNDER_DEPLOY = 0.60;
    /** Ignore utilisation on a resource type whose planned strength is below this (too small to judge). */
    private static final double MIN_PLANNED = 5.0;

    private final Optional<CapacityUtilizationProvider> capacityProvider;
    private final ResourceAssignmentRepository assignmentRepository;
    private final DailyProgressReportRepository dprRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Capacity Utilisation";
    }

    @Override
    public boolean supportsPortfolio() {
        return false;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        List<AgentFindingDraft> candidates = new ArrayList<>();
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            return new GatherResult(snapshot, candidates);
        }

        // Per-role efficiency + cost overrun, EXACTLY as the Capacity Util. tab computes it, via the port.
        List<RoleEfficiency> roles = capacityProvider
                .map(p -> p.cumulativeByRole(projectId))
                .orElse(List.of());
        if (roles.isEmpty()) {
            return new GatherResult(snapshot, candidates);
        }

        Instant validUntil = (ctx.now() == null ? Instant.now() : ctx.now()).plus(TTL);

        // ── snapshot + per-role efficiency / cost-overrun findings ──────────────────────
        ArrayNode roleArr = snapshot.putArray("roles");
        double mpBudget = 0, mpActual = 0, eqBudget = 0, eqActual = 0;
        for (RoleEfficiency r : roles) {
            ObjectNode n = roleArr.addObject();
            n.put("type", r.resourceType());
            n.put("role", r.roleName());
            n.put("efficiencyPct", round(r.efficiencyPct()));
            n.put("budgetedDays", round(r.budgetedDays()));
            n.put("actualDays", round(r.actualDays()));
            n.put("countedDays", round(r.countedDays()));
            n.put("costOverrun", round(r.costImplication()));

            if ("MANPOWER".equals(r.resourceType())) {
                mpBudget += r.budgetedDays();
                mpActual += r.actualDays();
            } else {
                eqBudget += r.budgetedDays();
                eqActual += r.actualDays();
            }

            if (r.actualDays() >= MIN_DAYS
                    && (r.costImplication() >= COST_OVERRUN_MIN || r.efficiencyPct() < SEVERE_INEFFICIENCY_PCT)) {
                candidates.add(efficiency(projectId, r, validUntil));
            }
        }
        snapshot.put("manpowerEfficiencyPct", round(pctOf(mpBudget, mpActual)));
        snapshot.put("equipmentEfficiencyPct", round(pctOf(eqBudget, eqActual)));

        // ── deployment utilisation (deployed nos ÷ planned nos) + under-deployment findings ─────
        double plannedManpower = 0, plannedEquipment = 0;
        for (ResourceAssignment a : assignmentRepository.findByProjectId(projectId)) {
            double nos = plannedNos(a);
            if (a.getManpowerRoleRateId() != null) plannedManpower += nos;
            else if (a.getEquipmentRoleVariantId() != null) plannedEquipment += nos;
        }
        long activeDays = dprRepository
                .findByProjectIdAndApprovalStatusOrderByReportDateAscIdAsc(projectId, DprApprovalStatus.APPROVED)
                .stream().map(DailyProgressReport::getReportDate).filter(Objects::nonNull).distinct().count();

        if (activeDays > 0) {
            // deployed = average nos per active day = Σ headcount-days ÷ active days.
            double deployedManpower = mpActual / activeDays;
            double deployedEquipment = eqActual / activeDays;
            addUtilisation(candidates, snapshot, projectId, "Manpower", "MANPOWER",
                    deployedManpower, plannedManpower, validUntil);
            addUtilisation(candidates, snapshot, projectId, "Equipment", "EQUIPMENT",
                    deployedEquipment, plannedEquipment, validUntil);
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private void addUtilisation(List<AgentFindingDraft> candidates, ObjectNode snapshot, UUID projectId,
                                String label, String type, double deployedNos, double plannedNos,
                                Instant validUntil) {
        if (plannedNos < MIN_PLANNED) {
            return;
        }
        double util = deployedNos / plannedNos;
        ObjectNode u = snapshot.putObject(type.toLowerCase(Locale.ROOT) + "Utilisation");
        u.put("deployedNos", round(deployedNos));
        u.put("plannedNos", round(plannedNos));
        u.put("utilisation", round(util));
        if (util < UNDER_DEPLOY) {
            candidates.add(underDeployment(projectId, label, type, deployedNos, plannedNos, util, validUntil));
        }
    }

    private AgentFindingDraft efficiency(UUID projectId, RoleEfficiency r, Instant validUntil) {
        double overrun = r.costImplication();
        double effPct = r.efficiencyPct();
        Severity severity = (overrun >= 8000 || effPct < 50) ? Severity.HIGH
                : (overrun >= 4000 || effPct < SEVERE_INEFFICIENCY_PCT) ? Severity.MEDIUM : Severity.LOW;
        String head = overrun > 0
                ? r.roleName() + " is running below its productivity norm — " + money0(overrun) + " cost overrun"
                : r.roleName() + " is running below its productivity norm (" + pct(effPct) + " of norm)";
        return new AgentFindingDraft(
                "RESOURCE_EFFICIENCY",
                "role:" + r.resourceType() + ":" + r.roleName(),
                severity,
                0.9,
                "Output vs productivity norm per resource-day (Capacity Utilisation), project-to-date",
                head,
                r.roleName() + " produced at " + pct(effPct) + " of its productivity norm across "
                        + fmt1(r.countedDays()) + " counted resource-days (norm expected " + fmt1(r.budgetedDays())
                        + " days for the output achieved)"
                        + (overrun > 0 ? ", a cost overrun of " + money0(overrun) + "." : "."),
                "The crew/plant was deployed for more resource-days than its output — valued at the productivity "
                        + "norm — should have required, so each unit of work cost more than planned.",
                "Below-norm efficiency inflates unit cost and feeds straight into the cost variance and margin; "
                        + "sustained, it is a direct and recoverable overspend.",
                "Review why " + r.roleName() + " is under norm on its activities — right-size the crew/plant, clear "
                        + "the blockers dragging output (materials, access, sequencing), or correct the productivity norm "
                        + "if it is mis-set.",
                List.of(
                        EvidenceRef.metric("Efficiency vs norm", pct(effPct)),
                        EvidenceRef.money("Cost overrun", BigDecimal.valueOf(overrun)),
                        EvidenceRef.metric("Counted days", fmt1(r.countedDays())),
                        EvidenceRef.metric("Norm-expected days", fmt1(r.budgetedDays())),
                        EvidenceRef.entity("Capacity Utilisation", "Open", "project", projectId,
                                "/projects/" + projectId + "/capacity-utilization")),
                Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft underDeployment(UUID projectId, String label, String type, double deployedNos,
                                              double plannedNos, double util, Instant validUntil) {
        Severity severity = util < 0.30 ? Severity.HIGH : util < 0.50 ? Severity.MEDIUM : Severity.LOW;
        return new AgentFindingDraft(
                "UNDER_DEPLOYMENT",
                "deployment:" + type,
                severity,
                0.85,
                "Deployed nos ÷ planned nos (deployed = average per active day), project-to-date",
                label + " deployed at " + pct(util * 100) + " of the planned strength",
                label + " is averaging " + fmt1(deployedNos) + " on site per active day against a planned strength of "
                        + fmt0(plannedNos) + " — only " + pct(util * 100) + " of the plan.",
                "Fewer " + label.toLowerCase(Locale.ROOT) + " resources are being mobilised than the resource plan "
                        + "sized for the work — absences, late mobilisation, or resources diverted elsewhere.",
                "Under-deploying against plan slows the work fronts those resources were sized for and pushes planned "
                        + "progress into later days, eroding schedule float even when the deployed crews look efficient.",
                "Confirm whether the plan or the mobilisation is wrong: close the deployment gap where the front is "
                        + "schedule-critical, or re-baseline the planned strength if it is overstated.",
                List.of(
                        EvidenceRef.metric("Deployment vs plan", pct(util * 100)),
                        EvidenceRef.metric("Deployed (avg/day)", fmt1(deployedNos)),
                        EvidenceRef.metric("Planned strength", fmt0(plannedNos)),
                        EvidenceRef.entity("Resource plan", "Open", "project", projectId,
                                "/projects/" + projectId + "/capacity-utilization")),
                Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()),
                validUntil);
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private static double plannedNos(ResourceAssignment a) {
        if (a.getPlannedUnits() != null) return a.getPlannedUnits();
        return a.getHeadcount() != null ? a.getHeadcount() : 0.0;
    }

    private static double pctOf(double budget, double actual) {
        return actual > 0 ? budget / actual * 100.0 : 0.0;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f%%", v);
    }

    private static String money0(double v) {
        return String.format(Locale.ROOT, "%,.0f", v);
    }

    private static String fmt0(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
