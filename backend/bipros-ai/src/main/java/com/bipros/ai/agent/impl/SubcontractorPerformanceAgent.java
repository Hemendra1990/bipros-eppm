package com.bipros.ai.agent.impl;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.project.domain.repository.DprSubContractorRepository;
import com.bipros.resource.domain.model.ActivitySubContractorAssignment;
import com.bipros.resource.domain.model.master.SubContractorMaster;
import com.bipros.resource.domain.repository.ActivitySubContractorAssignmentRepository;
import com.bipros.resource.domain.repository.SubContractorMasterRepository;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sub-Contractor Performance agent. Compares each sub-contractor's <em>committed quantity</em> (the
 * activity plan line) against what it has actually <em>delivered</em> (Σ approved-DPR sub-contractor
 * quantity), and flags the sub-contractors materially behind their commitment.
 *
 * <p>The only real sub-contract variance is DELIVERY quantity: the DPR sub-contractor line carries no
 * rate (actual cost is always qty × the plan's own rate), so a per-unit cost overrun cannot occur.
 * Delivery is judged against the ACTIVITY'S % COMPLETE — a sub-contractor at 16% on a finished
 * activity is under-delivering; the same 16% on a barely-started activity is just early progress.
 *
 * <ul>
 *   <li>{@code SUBCONTRACTOR_UNDER_DELIVERY} — sub-contractors whose delivery lags the activity's
 *       progress by a material margin, worst first.</li>
 * </ul>
 *
 * <p>Inputs: planned from {@link ActivitySubContractorAssignment}; actual from the approved DPR
 * sub-contractor lines. Dormant on projects with no sub-contractor assignments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubcontractorPerformanceAgent extends AbstractAgent {

    private static final String KEY = "subcontractor_performance";
    private static final Duration TTL = Duration.ofDays(7);

    /** Only judge delivery once the activity itself has progressed at least this far (% complete). */
    private static final double MIN_ACTIVITY_PROGRESS = 25.0;
    /** Delivery lagging the activity's % complete by at least this many points is under-delivery. */
    private static final double SHORTFALL_MARGIN = 20.0;
    private static final int MAX_EXAMPLES = 6;

    private final ActivitySubContractorAssignmentRepository scRepository;
    private final DprSubContractorRepository dprScRepository;
    private final ActivityRepository activityRepository;
    private final SubContractorMasterRepository scMasterRepository;
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

        List<ActivitySubContractorAssignment> rows = scRepository.findByProjectId(projectId);
        if (rows.isEmpty()) {
            // No sub-contractor assignments — genuinely nothing to judge (frontend renders the
            // "no data" card off an empty snapshot).
            return new GatherResult(snapshot, candidates);
        }

        Instant now = ctx.now() == null ? Instant.now() : ctx.now();
        Instant validUntil = now.plus(TTL);

        // Activity % complete + name, and sub-contractor names, for context/labels.
        Map<UUID, Double> pctByActivity = new HashMap<>();
        Map<UUID, String> nameByActivity = new HashMap<>();
        for (Activity a : activityRepository.findByProjectId(projectId)) {
            pctByActivity.put(a.getId(), a.getPercentComplete() == null ? 0d : a.getPercentComplete());
            nameByActivity.put(a.getId(), a.getName());
        }
        Set<UUID> masterIds = new HashSet<>();
        for (ActivitySubContractorAssignment a : rows) {
            if (a.getSubContractorMasterId() != null) masterIds.add(a.getSubContractorMasterId());
        }
        Map<UUID, String> scName = new HashMap<>();
        for (SubContractorMaster m : scMasterRepository.findAllById(masterIds)) {
            scName.put(m.getId(), m.getName());
        }

        double plannedCost = 0, actualCost = 0;
        List<Under> under = new ArrayList<>();
        for (ActivitySubContractorAssignment a : rows) {
            double rate = dbl(a.getRatePerUnit());
            double plannedUnits = dbl(a.getPlannedUnits());
            double pc = a.getPlannedCost() != null ? dbl(a.getPlannedCost()) : plannedUnits * rate;
            double actualUnits = dbl(dprScRepository
                    .sumQuantityByActivitySubContractorAssignmentIdApproved(a.getId()));
            double ac = actualUnits * rate;
            plannedCost += pc;
            actualCost += ac;

            if (plannedUnits <= 0) continue; // no committed quantity → can't judge delivery
            double deliveryPct = actualUnits / plannedUnits * 100.0;
            double activityPct = pctByActivity.getOrDefault(a.getActivityId(), 0d);
            double shortfall = activityPct - deliveryPct;

            // Under-delivering: the activity has meaningfully progressed but the sub-contractor's
            // delivery lags that progress by a material margin.
            if (activityPct >= MIN_ACTIVITY_PROGRESS && shortfall >= SHORTFALL_MARGIN) {
                under.add(new Under(
                        scName.getOrDefault(a.getSubContractorMasterId(), "Sub-contractor"),
                        a.getWorkTypeName(), nameByActivity.get(a.getActivityId()),
                        plannedUnits, actualUnits, deliveryPct, activityPct, shortfall,
                        a.getUnit() == null ? "units" : a.getUnit()));
            }
        }

        double aggDeliveryPct = plannedCost > 0 ? actualCost / plannedCost * 100.0 : 0;
        under.sort(Comparator.comparingDouble(Under::shortfall).reversed());
        snapshot.put("subContractorCount", rows.size());
        snapshot.put("plannedCost", round(plannedCost));
        snapshot.put("actualCost", round(actualCost));
        snapshot.put("deliveryPct", round(aggDeliveryPct));
        snapshot.put("underDeliveringCount", under.size());
        snapshot.put("worstShortfall", under.isEmpty() ? 0 : round(under.get(0).shortfall()));
        // Fingerprint the under-delivery set (5% shortfall bands, sorted) so a real delivery shift
        // that doesn't change the count still re-hashes the snapshot.
        List<Under> fingerprint = new ArrayList<>(under);
        fingerprint.sort(Comparator.comparing(u -> label(u).toLowerCase(Locale.ROOT)));
        ArrayNode bands = snapshot.putArray("underDeliveryBands");
        for (Under u : fingerprint) {
            ObjectNode e = bands.addObject();
            e.put("sc", label(u).toLowerCase(Locale.ROOT));
            e.put("band5", (int) Math.round(u.shortfall() / 5.0));
        }

        if (!under.isEmpty()) {
            candidates.add(underDelivery(projectId, under, validUntil));
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft underDelivery(UUID projectId, List<Under> under, Instant validUntil) {
        int n = under.size();
        Under worst = under.get(0);
        Severity severity = worst.shortfall() >= 50 ? Severity.HIGH
                : worst.shortfall() >= 30 ? Severity.MEDIUM : Severity.LOW;

        List<EvidenceRef> ev = new ArrayList<>();
        for (Under u : under.subList(0, Math.min(MAX_EXAMPLES, n))) {
            ev.add(EvidenceRef.metric(label(u),
                    "delivered " + fmt(u.actualUnits()) + " of " + fmt(u.plannedUnits()) + " " + u.unit()
                            + " (" + pct(u.deliveryPct()) + ") — activity " + pct(u.activityPct()) + " complete"));
        }
        ev.add(EvidenceRef.entity("Sub-contractor report", "Open", "project", projectId,
                "/projects/" + projectId + "/dbs"));

        return new AgentFindingDraft(
                "SUBCONTRACTOR_UNDER_DELIVERY", "PROJECT", severity, 0.9,
                "Sub-contractor delivered quantity (approved DPRs) vs its committed plan quantity, benchmarked "
                        + "against the activity's % complete",
                n + " sub-contract commitment" + (n == 1 ? " is" : "s are") + " under-delivering",
                worst.scName() + " has delivered " + fmt(worst.actualUnits()) + " of its committed "
                        + fmt(worst.plannedUnits()) + " " + worst.unit() + " (" + pct(worst.deliveryPct())
                        + ") on " + activityName(worst) + ", which is " + pct(worst.activityPct())
                        + " complete — a delivery shortfall of " + pct(worst.shortfall()) + " points"
                        + (n > 1 ? "; " + (n - 1) + " other commitment(s) are also behind." : "."),
                "The sub-contractor is executing less than its committed quantity relative to how far the activity "
                        + "has come — slow mobilisation, a stalled front, or a plan quantity that overstates the scope.",
                "Under-delivered sub-contract scope leaves work the company must either absorb with its own crews or "
                        + "carry as a gap — it distorts the committed-cost picture and can stall the activity's completion.",
                "Reconcile the committed quantity with what the sub-contractor has actually executed on approved DPRs: "
                        + "chase the shortfall where the scope is still live, or correct the plan quantity if it was overstated.",
                ev, Map.of("PROJECT_MANAGER", List.of(), "PLANNING_ENGINEER", List.of()), validUntil);
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private static String label(Under u) {
        String wt = u.workType() == null ? "" : " (" + u.workType() + ")";
        return u.scName() + wt;
    }

    private static String activityName(Under u) {
        return u.activityName() == null ? "its activity" : u.activityName();
    }

    private static double dbl(BigDecimal b) {
        return b == null ? 0 : b.doubleValue();
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String fmt(double v) {
        return v >= 100 ? String.format(Locale.ROOT, "%,.0f", v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v);
    }

    private record Under(String scName, String workType, String activityName,
                         double plannedUnits, double actualUnits, double deliveryPct,
                         double activityPct, double shortfall, String unit) {
    }
}
