package com.bipros.ai.agent.impl;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.support.CapacityUtilizationProvider;
import com.bipros.ai.agent.support.CapacityUtilizationProvider.SupervisorEfficiency;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Supervisor Performance agent. A deterministic {@link #gather} that ranks a project's supervisors
 * side-by-side and surfaces the comparison the AI chat could previously only answer on demand
 * ("compare supervisors") as a proactive board finding.
 *
 * <p>Data source: {@link DailyProgressReportRepository} — the supervisors are read from where the
 * field actually records them (the DPR's {@code supervisorUserId} / {@code supervisorName}), not
 * from an activity→resource assignment that many projects never populate. Each supervisor's DPRs are
 * joined to their activities to compute <em>unit-free, cross-comparable</em> metrics: progress
 * attributed by each supervisor's share of the executed quantity (not the shared activity
 * %-complete, which would credit every supervisor of an activity its full progress) and the share of
 * their activities running late. Against the <em>peer median</em> of the project's own supervisors it emits:
 *
 * <ul>
 *   <li>{@code SUPERVISOR_COMPARISON} — one scorecard (who leads / who lags on progress) whenever
 *       ≥2 supervisors carry enough work;</li>
 *   <li>{@code SUPERVISOR_UNDERPERFORMANCE} — per supervisor materially behind peers on progress or
 *       carrying a high share of delayed activities.</li>
 * </ul>
 *
 * <p>Comparison is relative to the project's own supervisors (a fair, self-normalising baseline), so
 * it needs no external norm. Confidence scales with how many active reporting days back the row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupervisorPerformanceAgent extends AbstractAgent {

    private static final String KEY = "supervisor_performance";
    private static final Duration TTL = Duration.ofDays(7);

    /** A supervisor needs at least this many supervised activities before a comparison is meaningful. */
    private static final int MIN_ACTIVITIES = 3;
    /** …and at least this many distinct days of reporting, so the row is not a one-off. */
    private static final int MIN_ACTIVE_DAYS = 5;
    /** Progress this many percentage-points below the peer median counts as materially behind. */
    private static final double PROGRESS_LAG_PP = 12.0;
    /** A delayed-activity share above this (and above peers) is a schedule-risk laggard. */
    private static final double DELAY_LAG_RATIO = 0.40;

    private final DailyProgressReportRepository dprRepository;
    private final ActivityRepository activityRepository;
    /** Canonical capacity engine — the ONLY source of resource-efficiency figures (never re-derived here). */
    private final Optional<CapacityUtilizationProvider> capacityProvider;
    private final ObjectMapper objectMapper;
    private final com.bipros.ai.agent.notify.StakeholderResolver stakeholderResolver;

    /**
     * Responsible-person routing (owner decision 2026-08-05): the lagging supervisor's direct
     * manager gets the finding — never the supervisor themselves; PM always; SITE_MANAGER seats
     * only as the fallback when no manager resolves (free-text supervisor, no team seat).
     */
    private Map<String, List<UUID>> stakeholdersFor(UUID projectId, UUID supervisorUserId) {
        return stakeholderResolver.pmPlusManagersOf(projectId, java.util.Collections.singleton(supervisorUserId));
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Supervisor Performance";
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
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

        Map<UUID, Activity> activityById = new HashMap<>();
        for (Activity a : activityRepository.findByProjectId(projectId)) {
            activityById.put(a.getId(), a);
        }

        // Group reported DPRs by supervisor (prefer the user id; fall back to the name string). Track each
        // supervisor's own executed qty per activity AND the project-wide executed qty per activity, so
        // progress can be attributed by contribution share (not by copying the shared activity %-complete).
        Map<String, Sup> bySupervisor = new LinkedHashMap<>();
        Map<UUID, BigDecimal> totalQtyByActivity = new HashMap<>();
        for (DailyProgressReport d : dprRepository.findByProjectIdOrderByReportDateAscIdAsc(projectId)) {
            // APPROVED only — an unapproved DPR is not yet an accepted fact about the site.
            if (d.getApprovalStatus() != DprApprovalStatus.APPROVED) continue;
            String key = d.getSupervisorUserId() != null
                    ? "u:" + d.getSupervisorUserId()
                    : (d.getSupervisorName() != null ? "n:" + d.getSupervisorName().toLowerCase(Locale.ROOT) : null);
            if (key == null) continue;
            Sup s = bySupervisor.computeIfAbsent(key, k -> new Sup(d.getSupervisorUserId(), d.getSupervisorName()));
            s.reports++;
            if (d.getReportDate() != null) s.activeDays.add(d.getReportDate());
            if (d.getActivityId() != null) {
                s.activityIds.add(d.getActivityId());
                if (d.getQtyExecuted() != null) {
                    s.totalQty = s.totalQty.add(d.getQtyExecuted());
                    s.qtyByActivity.merge(d.getActivityId(), d.getQtyExecuted(), BigDecimal::add);
                    totalQtyByActivity.merge(d.getActivityId(), d.getQtyExecuted(), BigDecimal::add);
                }
            }
        }

        // Score each supervisor by their OWN contribution: attribute each activity's %-complete weighted by
        // the supervisor's share of the executed quantity on that activity (share = supQty / totalQty). This
        // credits a supervisor for the work THEY did — a supervisor who merely dabbled in a near-complete
        // activity is no longer scored as if they finished it — instead of copying the shared activity
        // %-complete to every supervisor who touched it. Sole contributors (share = 1) score unchanged.
        List<Score> scores = new ArrayList<>();
        for (Sup s : bySupervisor.values()) {
            int activityCount = 0;
            double pctShareSum = 0;    // Σ (activity.pct × share)
            double pctShareDenom = 0;  // Σ share over activities carrying a %-complete
            int delayed = 0;
            for (UUID aid : s.activityIds) {
                Activity a = activityById.get(aid);
                if (a == null) continue;
                activityCount++;
                BigDecimal supQty = s.qtyByActivity.getOrDefault(aid, BigDecimal.ZERO);
                BigDecimal totQty = totalQtyByActivity.getOrDefault(aid, BigDecimal.ZERO);
                double share = totQty.signum() > 0 ? supQty.doubleValue() / totQty.doubleValue() : 1.0;
                if (a.getPercentComplete() != null) {
                    pctShareSum += a.getPercentComplete() * share;
                    pctShareDenom += share;
                }
                if (isDelayed(a, today)) delayed++;
            }
            if (activityCount < MIN_ACTIVITIES || s.activeDays.size() < MIN_ACTIVE_DAYS) continue;
            double avgPct = pctShareDenom > 0 ? pctShareSum / pctShareDenom : 0;
            double delayRatio = activityCount == 0 ? 0 : (double) delayed / activityCount;
            scores.add(new Score(s, activityCount, avgPct, delayed, delayRatio));
        }

        scores.sort(Comparator.comparingDouble((Score sc) -> sc.avgPct).reversed());

        // Resource efficiency for the SAME supervisors, read from the canonical capacity engine — the
        // second, independent dimension: progress says how much of the work is done, efficiency says
        // whether the crews hit the productivity norm while doing it.
        Map<UUID, SupervisorEfficiency> effById = loadEfficiency(projectId, scores);

        Instant validUntil = now.plus(TTL);
        ArrayNode rowsNode = snapshot.putArray("supervisors");
        for (Score sc : scores) {
            ObjectNode n = rowsNode.addObject();
            n.put("name", sc.sup.name());
            n.put("activities", sc.activityCount);
            n.put("activeDays", sc.sup.activeDays.size());
            n.put("avgPercentComplete", round(sc.avgPct));
            n.put("delayed", sc.delayed);
            n.put("delayRatio", round(sc.delayRatio));
            SupervisorEfficiency eff = effById.get(sc.sup.userId());
            if (eff != null) {
                putPct(n, "manpowerEfficiencyPct", eff.manpowerEfficiencyPct());
                putPct(n, "equipmentEfficiencyPct", eff.equipmentEfficiencyPct());
                putPct(n, "efficiencyPct", eff.overallEfficiencyPct());
                n.put("countedDays", round(eff.countedDays()));
            }
        }
        snapshot.put("supervisorCount", scores.size());

        if (scores.size() < 2) {
            return new GatherResult(snapshot, candidates); // nothing to compare against
        }

        double medianPct = median(scores.stream().map(sc -> sc.avgPct).sorted().toList());
        double medianDelay = median(scores.stream().map(sc -> sc.delayRatio).sorted().toList());
        snapshot.put("medianAvgPercentComplete", round(medianPct));
        snapshot.put("medianDelayRatio", round(medianDelay));

        // --- Scorecard summary (SUPERVISOR_COMPARISON) ---
        Score best = scores.get(0);
        Score worst = scores.get(scores.size() - 1);
        candidates.add(comparison(projectId, scores, best, worst, medianPct, effById, validUntil));

        // --- Per-supervisor underperformance (SUPERVISOR_UNDERPERFORMANCE) ---
        for (Score sc : scores) {
            // Absolute pp gap catches laggards on mature projects; the relative "half the peer median"
            // test catches them on early-stage projects where every value is small and pp gaps compress.
            boolean progressLag = sc.avgPct < medianPct
                    && ((medianPct - sc.avgPct) >= PROGRESS_LAG_PP
                        || (medianPct > 0 && sc.avgPct <= medianPct * 0.5 && (medianPct - sc.avgPct) >= 5.0));
            boolean delayLag = sc.delayRatio >= DELAY_LAG_RATIO && sc.delayRatio > medianDelay + 0.10;
            if (progressLag || delayLag) {
                candidates.add(underperformance(projectId, sc, medianPct, medianDelay,
                        progressLag, delayLag, effById.get(sc.sup.userId()), validUntil));
            }
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft comparison(UUID projectId, List<Score> scores, Score best, Score worst,
                                         double medianPct, Map<UUID, SupervisorEfficiency> effById,
                                         Instant validUntil) {
        int count = scores.size();
        List<EvidenceRef> evidence = new ArrayList<>();
        // Chart every supervisor on progress — the dimension with real spread (efficiency sits in a
        // narrow band, so bars would be indistinguishable). Median is the reference line.
        evidence.add(EvidenceRef.chart("Progress by supervisor", new EvidenceRef.Series(
                "COLUMN", "%",
                scores.stream()
                        .map(sc -> new EvidenceRef.Series.Point(sc.sup.name(), round(sc.avgPct)))
                        .toList(),
                round(medianPct), "Peer median " + pctv(medianPct))));
        evidence.add(EvidenceRef.metric("Supervisors compared", String.valueOf(count)));
        evidence.add(EvidenceRef.metric("Peer median progress", pctv(medianPct)));
        // One row per supervisor carrying BOTH dimensions, each deep-linking into the Capacity Util.
        // tab filtered to that supervisor — where the full per-trade / per-equipment table already lives.
        for (Score sc : scores) {
            evidence.add(supervisorEntity(projectId, sc.sup, rank(sc, best, worst),
                    pctv(sc.avgPct) + " progress · " + effv(effById.get(sc.sup.userId())) + " efficiency"));
        }
        return new AgentFindingDraft(
                "SUPERVISOR_COMPARISON",
                "PROJECT",
                Severity.INFO,
                0.9,
                "Ranked across " + count + " supervisors by progress attributed to each supervisor's own share "
                        + "of the executed work; efficiency read from the Capacity Utilization engine",
                "Supervisor scorecard: " + best.sup.name() + " leads, " + worst.sup.name() + " lags",
                count + " supervisors were compared on two independent measures — the average progress of "
                        + "their activities, and resource efficiency against the productivity norm. "
                        + best.sup.name() + " leads at " + pctv(best.avgPct) + " avg complete ("
                        + best.delayed + " late, " + effv(effById.get(best.sup.userId())) + " efficiency); "
                        + worst.sup.name() + " trails at " + pctv(worst.avgPct) + " (" + worst.delayed
                        + " late, " + effv(effById.get(worst.sup.userId())) + " efficiency), against a peer "
                        + "median progress of " + pctv(medianPct) + ".",
                "Progress per supervisor varies with crew mix, activity difficulty and site conditions. "
                        + "Progress and efficiency answer different questions: progress is how much of the "
                        + "work is finished, efficiency is whether the crews hit the norm while doing it — a "
                        + "supervisor can be efficient yet behind if they were given less work or are blocked.",
                "Comparing supervisors head-to-head highlights who needs support and who is running work well — "
                        + "the same crews redeployed toward the lagging front recover time at no extra cost.",
                "Review " + worst.sup.name() + "'s activities with the project manager; consider pairing them "
                        + "with " + best.sup.name() + " or re-balancing crews toward the schedule-critical front. "
                        + "Open any supervisor below to see their trade-by-trade efficiency.",
                evidence,
                stakeholdersFor(projectId, worst.sup.userId()),
                validUntil);
    }

    /** Row label; the card renders it as "{label} — {value}", so the rank reads as a suffix. */
    private static String rank(Score sc, Score best, Score worst) {
        if (sc == best) return sc.sup.name() + " (top)";
        if (sc == worst) return sc.sup.name() + " (lagging)";
        return sc.sup.name();
    }

    private AgentFindingDraft underperformance(UUID projectId, Score sc, double medianPct, double medianDelay,
                                               boolean progressLag, boolean delayLag,
                                               SupervisorEfficiency eff, Instant validUntil) {
        double gapPP = medianPct - sc.avgPct;
        boolean farBelow = medianPct > 0 && sc.avgPct <= medianPct * 0.4;
        Severity severity;
        if (gapPP >= 25 || sc.delayRatio >= 0.6) severity = Severity.HIGH;
        else if (gapPP >= 15 || sc.delayRatio >= 0.45 || farBelow) severity = Severity.MEDIUM;
        else severity = Severity.LOW;

        String dimension = progressLag && delayLag ? "progress and on-time delivery"
                : progressLag ? "progress" : "on-time delivery";

        List<EvidenceRef> evidence = new ArrayList<>();
        evidence.add(EvidenceRef.metric(sc.sup.name() + " avg progress", pctv(sc.avgPct)));
        evidence.add(EvidenceRef.metric("Peer median progress", pctv(medianPct)));
        evidence.add(EvidenceRef.metric("Delayed activities", sc.delayed + " of " + sc.activityCount
                + " (" + pctv(sc.delayRatio * 100) + ")"));
        evidence.add(EvidenceRef.metric("Active reporting days", String.valueOf(sc.sup.activeDays.size())));
        if (eff != null && eff.overallEfficiencyPct() != null) {
            evidence.add(EvidenceRef.metric("Resource efficiency", effv(eff)));
        }
        evidence.add(supervisorEntity(projectId, sc.sup, "Supervisor",
                pctv(sc.avgPct) + " progress · " + effv(eff) + " efficiency"));

        return new AgentFindingDraft(
                "SUPERVISOR_UNDERPERFORMANCE",
                sc.sup.userId() != null ? "supervisor:" + sc.sup.userId() : "supervisor:" + sc.sup.name(),
                severity,
                confidenceForDays(sc.sup.activeDays.size()),
                "Compared against the project's peer supervisor median",
                sc.sup.name() + " is behind peers on " + dimension,
                sc.sup.name() + " runs " + pctv(sc.avgPct) + " average progress (peer median " + pctv(medianPct)
                        + ") with " + sc.delayed + " of " + sc.activityCount + " activities running late, across "
                        + sc.sup.activeDays.size() + " reporting days.",
                "This front is advancing more slowly and slipping more activities than comparable supervisors on "
                        + "the same project — a resourcing, sequencing or site-access constraint concentrated here.",
                "Sustained under-performance on the schedule-critical front risks milestone slippage concentrated "
                        + "under one supervisor — the earliest, most contained place to intervene before it spreads to the plan.",
                "Sit with " + sc.sup.name() + " to unblock the lagging activities (crew size, materials, sequencing, "
                        + "access); reassign float or add a lead hand where the " + dimension + " gap is widest.",
                evidence,
                stakeholdersFor(projectId, sc.sup.userId()),
                validUntil);
    }

    /**
     * A supervisor row that deep-links into Capacity Utilization pre-filtered to them — that tab already
     * renders the full trade-by-trade and equipment-by-equipment breakdown, so the drill-down reuses it
     * rather than rebuilding a table on the finding card.
     */
    private static EvidenceRef supervisorEntity(UUID projectId, Sup sup, String label, String value) {
        String base = "/projects/" + projectId + "/capacity-utilization";
        String link = sup.userId() != null ? base + "?supervisorUserId=" + sup.userId() : base;
        return EvidenceRef.entity(label, value, "user", sup.userId(), link);
    }

    /** Efficiency for the scored supervisors, straight from the canonical engine. Empty when unavailable. */
    private Map<UUID, SupervisorEfficiency> loadEfficiency(UUID projectId, List<Score> scores) {
        List<UUID> ids = scores.stream()
                .map(sc -> sc.sup.userId())
                .filter(java.util.Objects::nonNull)
                .toList();
        if (capacityProvider.isEmpty() || ids.isEmpty()) {
            return Map.of();
        }
        try {
            Map<UUID, SupervisorEfficiency> out = new LinkedHashMap<>();
            for (SupervisorEfficiency e : capacityProvider.get().cumulativeBySupervisor(projectId, ids)) {
                out.put(e.supervisorUserId(), e);
            }
            return out;
        } catch (RuntimeException ex) {
            // Efficiency is a supplement — never fail the whole progress comparison over it.
            log.warn("Supervisor efficiency unavailable for project {}: {}", projectId, ex.toString());
            return Map.of();
        }
    }

    /** Overall efficiency for display, or "n/a" when the supervisor has no norm-resolved resource days. */
    private static String effv(SupervisorEfficiency eff) {
        return eff == null || eff.overallEfficiencyPct() == null
                ? "n/a"
                : String.format(Locale.ROOT, "%.1f%%", eff.overallEfficiencyPct());
    }

    private static void putPct(ObjectNode node, String field, Double pct) {
        if (pct != null) {
            node.put(field, round(pct));
        }
    }

    private static boolean isDelayed(Activity a, LocalDate today) {
        if (a.getActualFinishDate() != null && a.getPlannedFinishDate() != null
                && a.getActualFinishDate().isAfter(a.getPlannedFinishDate())) {
            return true;
        }
        return a.getActualFinishDate() == null && a.getPlannedFinishDate() != null
                && a.getPlannedFinishDate().isBefore(today)
                && (a.getPercentComplete() == null || a.getPercentComplete() < 100.0);
    }

    /** Confidence rises with the supervisor's reporting-day sample: 5 days ≈ 0.60, 40+ ≈ 0.90. */
    private static double confidenceForDays(int days) {
        return Math.min(0.90, 0.50 + days / 100.0 * 1.0);
    }

    private static double median(List<Double> sortedAsc) {
        int n = sortedAsc.size();
        if (n == 0) return 0;
        return n % 2 == 1 ? sortedAsc.get(n / 2) : (sortedAsc.get(n / 2 - 1) + sortedAsc.get(n / 2)) / 2.0;
    }

    private static String pctv(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Per-supervisor DPR rollup. */
    private static final class Sup {
        private final UUID userId;
        private final String name;
        final Set<LocalDate> activeDays = new HashSet<>();
        final Set<UUID> activityIds = new HashSet<>();
        final Map<UUID, BigDecimal> qtyByActivity = new HashMap<>();
        int reports;
        BigDecimal totalQty = BigDecimal.ZERO;

        Sup(UUID userId, String name) {
            this.userId = userId;
            this.name = name;
        }

        UUID userId() {
            return userId;
        }

        String name() {
            return name == null || name.isBlank() ? "Unnamed supervisor" : name;
        }
    }

    /** A scored supervisor ready for comparison. */
    private record Score(Sup sup, int activityCount, double avgPct, int delayed, double delayRatio) {
    }
}
