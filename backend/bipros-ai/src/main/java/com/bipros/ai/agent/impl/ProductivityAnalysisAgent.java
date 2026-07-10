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
import com.bipros.resource.domain.service.ProductivityNormLookupService;
import com.bipros.resource.domain.service.ResolvedNorm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Productivity Analysis agent. A deterministic {@link #gather} that compares each activity's
 * <em>actual</em> daily output against its <em>planned productivity norm</em> and flags the
 * activities running materially below norm — i.e. declining or under-performing production.
 *
 * <p>Actual output is read from the Daily Progress Reports (quantity executed per reporting day per
 * activity); the planned norm is resolved from the productivity-norm master via
 * {@link ProductivityNormLookupService#resolveByName} (the unscoped, activity-level output-per-day).
 * Only activities whose DPR unit matches the norm unit are compared, so the ratio is apples-to-apples.
 *
 * <ul>
 *   <li>{@code PRODUCTIVITY_BELOW_NORM} — activities whose actual output-per-day is materially below
 *       their norm, with the worst first.</li>
 * </ul>
 *
 * <p>Dormant on projects without a productivity-norm master (nothing to compare against). The output
 * is crew-size-neutral (norm is a standard-crew figure), so confidence is high but not exact.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductivityAnalysisAgent extends AbstractAgent {

    private static final String KEY = "productivity_analysis";
    private static final Duration TTL = Duration.ofDays(7);

    /** An activity needs at least this many reporting days before its productivity is judged. */
    private static final int MIN_DAYS = 3;
    /** Actual output-per-day this far below norm (fraction) is "below norm". */
    private static final double BELOW_MARGIN = 0.20;
    private static final int MAX_EXAMPLES = 6;

    private final DailyProgressReportRepository dprRepository;
    private final ProductivityNormLookupService normLookup;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Productivity Analysis";
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

        // Aggregate actual output per activity from reported DPRs.
        Map<String, Act> byActivity = new LinkedHashMap<>();
        for (DailyProgressReport d : dprRepository.findByProjectIdOrderByReportDateAscIdAsc(projectId)) {
            DprApprovalStatus st = d.getApprovalStatus();
            if (st != DprApprovalStatus.SUBMITTED && st != DprApprovalStatus.APPROVED) continue;
            if (d.getActivityName() == null || d.getQtyExecuted() == null) continue;
            String key = d.getActivityName().toLowerCase(Locale.ROOT);
            Act a = byActivity.computeIfAbsent(key, k -> new Act(d.getActivityName(), d.getActivityId(), d.getUnit()));
            a.totalQty = a.totalQty.add(d.getQtyExecuted());
            if (d.getReportDate() != null) {
                a.days.add(d.getReportDate());
                // Crew-day = one reporting front on one date. A "front" is the supervisor when known, else the
                // chainage/side key — so N parallel crews on a date count as N crew-days and the per-crew-day
                // output isn't inflated by summing every front's output over calendar days.
                Object front = d.getSupervisorUserId() != null
                        ? d.getSupervisorUserId()
                        : Arrays.asList(d.getChainageFromM(), d.getChainageToM(), d.getSide());
                a.crewDays.add(Arrays.asList(d.getReportDate(), front));
            }
        }

        int normsResolved = 0;
        List<Lag> below = new ArrayList<>();
        int aboveOrOn = 0;
        for (Act a : byActivity.values()) {
            if (a.days.size() < MIN_DAYS) continue;
            ResolvedNorm norm = resolveNorm(a.name);
            if (norm == null || norm.outputPerDay() == null || norm.outputPerDay().signum() <= 0) continue;
            // Only compare when the units line up — but normalise first, since the DPR ("cu.m.") and the
            // norm master ("cum") spell the same unit differently. Skip only on a genuine unit clash.
            if (unitClash(a.unit, norm.unit())) continue;
            normsResolved++;

            double normPerDay = norm.outputPerDay().doubleValue();
            // Per crew-day (not calendar day): total output ÷ distinct (date, front) pairs, so multiple
            // parallel crews on one date don't inflate the ratio against the single-crew norm.
            double actualPerDay = a.totalQty.doubleValue() / a.crewDays.size();
            double ratio = actualPerDay / normPerDay;
            if (ratio < 1.0 - BELOW_MARGIN) {
                below.add(new Lag(a, actualPerDay, normPerDay, ratio));
            } else {
                aboveOrOn++;
            }
        }

        snapshot.put("activitiesWithNorm", normsResolved);
        snapshot.put("belowNorm", below.size());
        snapshot.put("atOrAboveNorm", aboveOrOn);

        // Per-activity fingerprint of the below-norm set, so a DPR that materially moves an
        // activity's output-per-day WITHOUT flipping its below/above-norm bucket (or that swaps
        // one below-norm activity for another while the 3 counts stay identical) still changes the
        // snapshot hash and re-runs the agent. Ratio is bucketed into 5% bands (band = round(ratio/0.05)):
        // idle-day / ±0.01 DPRs stay in-band and don't churn, but a real productivity shift crosses a
        // band. Sorted by activity key because `below` is in DPR-insertion order (byActivity is a
        // LinkedHashMap), not sorted — the explicit sort is what makes the serialized hash deterministic.
        List<Lag> fingerprint = new ArrayList<>(below);
        fingerprint.sort(Comparator.comparing((Lag l) -> l.a.name == null ? "" : l.a.name.toLowerCase(Locale.ROOT)));
        ArrayNode belowBands = snapshot.putArray("belowNormBands");
        for (Lag l : fingerprint) {
            ObjectNode e = belowBands.addObject();
            e.put("activity", l.a.name == null ? "" : l.a.name.toLowerCase(Locale.ROOT));
            e.put("band5", (int) Math.round(l.ratio / 0.05));
        }

        if (!below.isEmpty()) {
            candidates.add(belowNorm(projectId, below, normsResolved, validUntil));
        }
        // Dormant when no activity resolves a norm (no productivity master) — no candidates.

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    /** True only when both units are present and, once normalised (lowercased, punctuation stripped),
     *  clearly differ — e.g. "cu.m." and "cum" do NOT clash, but "cum" and "sqm" do. */
    private static boolean unitClash(String a, String b) {
        if (a == null || a.isBlank() || b == null || b.isBlank()) return false;
        return !normUnit(a).equals(normUnit(b));
    }

    private static String normUnit(String u) {
        return u.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private ResolvedNorm resolveNorm(String activityName) {
        try {
            return normLookup.resolveByName(activityName, null);
        } catch (Exception e) {
            log.debug("norm resolve failed for '{}': {}", activityName, e.getMessage());
            return null;
        }
    }

    private AgentFindingDraft belowNorm(UUID projectId, List<Lag> below, int withNorm, Instant validUntil) {
        below.sort(Comparator.comparingDouble(l -> l.ratio));
        int n = below.size();
        double worstRatio = below.get(0).ratio;
        double share = withNorm == 0 ? 0 : (double) n / withNorm;
        Severity severity = worstRatio < 0.5 || share >= 0.5 ? Severity.HIGH
                : worstRatio < 0.7 || n >= 3 ? Severity.MEDIUM : Severity.LOW;

        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Activities below norm", n + " of " + withNorm + " with a norm"));
        for (Lag l : below.subList(0, Math.min(MAX_EXAMPLES, n))) {
            ev.add(EvidenceRef.entity(activityLabel(l.a),
                    fmt(l.actualPerDay) + " vs norm " + fmt(l.normPerDay) + " " + unit(l.a)
                            + "/day (" + pct(l.ratio * 100) + " of norm)",
                    "activity", l.a.activityId,
                    l.a.activityId == null ? "/projects/" + projectId + "/dpr"
                            : "/projects/" + projectId + "/schedule?focus=" + l.a.activityId));
        }

        Lag worst = below.get(0);
        return new AgentFindingDraft(
                "PRODUCTIVITY_BELOW_NORM", "PROJECT", severity, 0.8,
                "Actual output-per-day (from DPRs) vs the activity's productivity norm",
                n + " activit" + (n == 1 ? "y is" : "ies are") + " producing below their productivity norm",
                n + " activit" + (n == 1 ? "y is" : "ies are") + " executing below the planned productivity norm; "
                        + "the worst, " + activityLabel(worst.a) + ", is running at " + pct(worst.ratio * 100)
                        + " of norm (" + fmt(worst.actualPerDay) + " vs " + fmt(worst.normPerDay) + " "
                        + unit(worst.a) + "/day).",
                "Output per crew-day is lagging the benchmark — a sign of a weaker crew, harder conditions, rework, "
                        + "or tool/material gaps on these fronts rather than normal variation.",
                "Below-norm productivity silently extends every affected activity's duration and inflates unit cost; "
                        + "it is the earliest measurable warning of schedule and cost slip.",
                "Review the lowest-ratio activities with the supervisor for the cause (crew skill, ground, rework, "
                        + "materials) and set a recovery target back toward norm; re-baseline the norm only if it is "
                        + "genuinely unachievable for this site.",
                ev, Map.of("PLANNING_ENGINEER", List.of(), "SITE_MANAGER", List.of()), validUntil);
    }

    private static String activityLabel(Act a) {
        String name = a.name == null ? "activity" : a.name;
        return name.length() > 48 ? name.substring(0, 47) + "…" : name;
    }

    private static String unit(Act a) {
        return a.unit == null ? "units" : a.unit;
    }

    private static String fmt(double v) {
        return v >= 100 ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v);
    }

    private static final class Act {
        final String name;
        final UUID activityId;
        final String unit;
        BigDecimal totalQty = BigDecimal.ZERO;
        final Set<LocalDate> days = new HashSet<>();
        /** Distinct (reportDate, front) pairs — the crew-day denominator for output-per-day. */
        final Set<Object> crewDays = new HashSet<>();

        Act(String name, UUID activityId, String unit) {
            this.name = name;
            this.activityId = activityId;
            this.unit = unit;
        }
    }

    private record Lag(Act a, double actualPerDay, double normPerDay, double ratio) {
    }
}
