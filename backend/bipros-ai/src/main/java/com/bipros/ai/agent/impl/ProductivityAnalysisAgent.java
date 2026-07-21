package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.support.CapacityUtilizationProvider;
import com.bipros.ai.agent.support.CapacityUtilizationProvider.ActivityEfficiency;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Productivity Analysis agent. Flags the work-activities whose <em>actual output-per-resource-day</em>
 * is running materially below their <em>planned productivity norm</em> — i.e. under-performing
 * production, per work-front.
 *
 * <p>Numbers come EXACTLY from the canonical Capacity Utilisation computation via
 * {@link CapacityUtilizationProvider#cumulativeByActivity} (a Dependency-Inversion port; the heavy
 * allocator + norm resolution live in the unreachable {@code bipros-reporting} module). That is the
 * SAME allocator, norm resolution and efficiency formula the Capacity Util. tab uses, only regrouped
 * by work-activity — so the agent never re-derives, and never drifts from, the tab. The per-role
 * Capacity Utilisation agent answers "which resource ROLE is inefficient"; this answers "which
 * ACTIVITY is producing below norm", split by resource type so the lagging side is named.
 *
 * <ul>
 *   <li>{@code PRODUCTIVITY_BELOW_NORM} — activities whose manpower or equipment efficiency is
 *       materially below norm, worst first.</li>
 * </ul>
 *
 * <p>Dormant on projects with no norm-resolved activity output (nothing to compare against).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductivityAnalysisAgent extends AbstractAgent {

    private static final String KEY = "productivity_analysis";
    private static final Duration TTL = Duration.ofDays(7);

    /** Efficiency at or below this (% of norm) is "below norm". */
    private static final double BELOW_PCT = 80.0;
    /** A side needs at least this many tracked resource-days before its efficiency is judged. */
    private static final double MIN_ACTUAL_DAYS = 5.0;
    private static final int MAX_EXAMPLES = 6;

    private final Optional<CapacityUtilizationProvider> capacityProvider;
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

        // Canonical per-(work-activity, resource-type) efficiency — same figures as the Capacity tab.
        List<ActivityEfficiency> rows = capacityProvider
                .map(p -> p.cumulativeByActivity(projectId))
                .orElse(List.of());

        // Fold the two resource-type rows into one per work-activity.
        Map<UUID, Act> byActivity = new LinkedHashMap<>();
        for (ActivityEfficiency r : rows) {
            Act a = byActivity.computeIfAbsent(r.workActivityId(), k -> new Act(r.activityName()));
            if ("MANPOWER".equals(r.resourceType())) {
                a.mpEff = r.efficiencyPct();
                a.mpDays = r.actualDays();
                a.mpBudget = r.budgetDays();
            } else {
                a.eqEff = r.efficiencyPct();
                a.eqDays = r.actualDays();
                a.eqBudget = r.budgetDays();
            }
        }

        int withNorm = 0;
        int aboveOrOn = 0;
        List<Lag> below = new ArrayList<>();
        for (Act a : byActivity.values()) {
            Lag worst = worstSide(a);
            if (worst == null) continue; // no side with enough tracked days to judge
            withNorm++;
            if (worst.effPct() < BELOW_PCT) {
                below.add(worst);
            } else {
                aboveOrOn++;
            }
        }

        snapshot.put("activitiesWithNorm", withNorm);
        snapshot.put("belowNorm", below.size());
        snapshot.put("atOrAboveNorm", aboveOrOn);
        // Fingerprint the below-norm set (5% efficiency bands, sorted) so a real productivity shift
        // that doesn't flip the below/above counts still changes the snapshot hash and re-runs.
        List<Lag> fingerprint = new ArrayList<>(below);
        fingerprint.sort(Comparator.comparing(l -> l.name() == null ? "" : l.name().toLowerCase(Locale.ROOT)));
        ArrayNode bands = snapshot.putArray("belowNormBands");
        for (Lag l : fingerprint) {
            ObjectNode e = bands.addObject();
            e.put("activity", l.name() == null ? "" : l.name().toLowerCase(Locale.ROOT));
            e.put("band5", (int) Math.round(l.effPct() / 5.0));
        }

        if (!below.isEmpty()) {
            candidates.add(belowNorm(projectId, below, withNorm, validUntil));
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    /** The worse-performing resource side of an activity, considering only sides with enough tracked
     *  resource-days to judge; {@code null} when neither side qualifies. */
    private static Lag worstSide(Act a) {
        Lag worst = null;
        if (a.mpEff != null && a.mpDays >= MIN_ACTUAL_DAYS) {
            worst = new Lag(a.name, "manpower", a.mpEff, a.mpBudget, a.mpDays);
        }
        if (a.eqEff != null && a.eqDays >= MIN_ACTUAL_DAYS
                && (worst == null || a.eqEff < worst.effPct())) {
            worst = new Lag(a.name, "equipment", a.eqEff, a.eqBudget, a.eqDays);
        }
        return worst;
    }

    private AgentFindingDraft belowNorm(UUID projectId, List<Lag> below, int withNorm, Instant validUntil) {
        below.sort(Comparator.comparingDouble(Lag::effPct));
        int n = below.size();
        Lag worst = below.get(0);
        double share = withNorm == 0 ? 0 : (double) n / withNorm;
        Severity severity = worst.effPct() < 50 || share >= 0.5 ? Severity.HIGH
                : worst.effPct() < 70 || n >= 3 ? Severity.MEDIUM : Severity.LOW;

        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Activities below norm", n + " of " + withNorm + " with a norm"));
        for (Lag l : below.subList(0, Math.min(MAX_EXAMPLES, n))) {
            ev.add(EvidenceRef.entity(activityLabel(l.name()),
                    l.side() + " " + pct(l.effPct()) + " of norm (norm-expected " + fmt(l.budgetDays())
                            + " vs " + fmt(l.actualDays()) + " resource-days)",
                    "activity", null,
                    "/projects/" + projectId + "/capacity-utilization"));
        }

        return new AgentFindingDraft(
                "PRODUCTIVITY_BELOW_NORM", "PROJECT", severity, 0.85,
                "Output per resource-day vs the productivity norm (canonical Capacity Utilisation), project-to-date",
                n + " activit" + (n == 1 ? "y is" : "ies are") + " producing below their productivity norm",
                n + " activit" + (n == 1 ? "y is" : "ies are") + " executing below the planned productivity norm; "
                        + "the worst, " + activityLabel(worst.name()) + ", is running its " + worst.side()
                        + " at " + pct(worst.effPct()) + " of norm (norm expected " + fmt(worst.budgetDays())
                        + " resource-days for the output, but " + fmt(worst.actualDays()) + " were deployed).",
                "Output per resource-day is lagging the benchmark — a sign of a weaker crew/plant, harder "
                        + "conditions, rework, or tool/material gaps on these fronts rather than normal variation.",
                "Below-norm productivity silently extends every affected activity's duration and inflates its unit "
                        + "cost; it is the earliest measurable warning of schedule and cost slip.",
                "Review the lowest-efficiency activities with the supervisor for the cause (crew skill, ground, "
                        + "rework, materials) and set a recovery target back toward norm; re-baseline the norm only if "
                        + "it is genuinely unachievable for this site.",
                ev, Map.of("PLANNING_ENGINEER", List.of(), "SITE_MANAGER", List.of()), validUntil);
    }

    private static String activityLabel(String name) {
        String n = name == null ? "activity" : name;
        return n.length() > 48 ? n.substring(0, 47) + "…" : n;
    }

    private static String fmt(double v) {
        return v >= 100 ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v);
    }

    private static final class Act {
        final String name;
        Double mpEff;
        double mpDays;
        double mpBudget;
        Double eqEff;
        double eqDays;
        double eqBudget;

        Act(String name) {
            this.name = name;
        }
    }

    private record Lag(String name, String side, double effPct, double budgetDays, double actualDays) {
    }
}
