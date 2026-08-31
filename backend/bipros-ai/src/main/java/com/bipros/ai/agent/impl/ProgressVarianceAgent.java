package com.bipros.ai.agent.impl;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityType;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.support.CanonicalEvm;
import com.bipros.cost.application.dto.PeriodPerformanceRollupDto;
import com.bipros.cost.application.service.PerformanceRollupService;
import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.evm.domain.repository.EvmCalculationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Progress Variance agent. Compares the project's <em>actual</em> earned progress against the
 * <em>planned</em> schedule and reports the schedule variance plus the impact on milestones.
 *
 * <p>Planned-vs-actual is read from Earned Value: the latest project-level {@link EvmCalculation}
 * carries the planned value (PV — where the schedule says the project should be), earned value
 * (EV — what has actually been achieved) and the schedule performance index (SPI = EV/PV). This is
 * the source of truth for "actual progress vs the planned schedule" — Activity plan dates are often
 * un-scheduled, whereas the EVM roll-up always reflects the baselined plan.
 *
 * <ul>
 *   <li>{@code SCHEDULE_PROGRESS_VARIANCE} — earned vs planned progress and SPI for the project;</li>
 *   <li>{@code MILESTONE_AT_RISK} — milestones whose planned date has passed while still open.</li>
 * </ul>
 *
 * <p>SPI/SV are stored EVM facts, so confidence is high; the milestone check is a direct date fact.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgressVarianceAgent extends AbstractAgent {

    private static final String KEY = "progress_variance";
    private static final Duration TTL = Duration.ofDays(7);

    /** Cap on milestone examples listed as evidence. */
    private static final int MAX_EXAMPLES = 6;

    private final EvmCalculationRepository evmRepository;
    private final ActivityRepository activityRepository;
    private final CanonicalEvm canonicalEvm;
    private final PerformanceRollupService performanceRollupService;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Progress Variance";
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
        Instant validUntil = now.plus(TTL);

        // --- Schedule progress variance from the canonical cost/EVM engine (identical to the Costs/EVM tab) ---
        CanonicalEvm.Snapshot evm = canonicalEvm.of(projectId);
        if (evm != null && evm.bac().signum() > 0 && evm.pv().signum() > 0) {
            double pv = evm.pv().doubleValue();
            double ev = evm.ev().doubleValue();
            double spi = evm.spi() != null ? evm.spi().doubleValue() : (pv > 0 ? ev / pv : 0.0);
            double plannedPct = evm.plannedPct();
            double earnedPct = evm.earnedPct();
            double sv = evm.sv().doubleValue();

            snapshot.put("pv", round(pv));
            snapshot.put("ev", round(ev));
            snapshot.put("bac", round(evm.bac().doubleValue()));
            snapshot.put("spi", round(spi));
            snapshot.put("plannedPctOfBudget", round(plannedPct));
            snapshot.put("earnedPctOfBudget", round(earnedPct));
            candidates.add(scheduleVariance(projectId, spi, sv, plannedPct, earnedPct, today,
                    spiHistorySeries(projectId), validUntil));
        }

        List<Activity> activities = activityRepository.findByProjectId(projectId);

        // --- Per-activity ahead / on-track / delayed from per-activity EVM SPI ---
        // Dormant on projects whose planned value is only rolled up at project level: an activity with
        // PV = 0 has no time-phased plan to compare against and is skipped. It activates automatically
        // once planned value is distributed to activities (time-phasing / PV distribution).
        Map<UUID, String> nameById = new LinkedHashMap<>();
        for (Activity a : activities) nameById.put(a.getId(), activityLabel(a));

        Map<UUID, EvmCalculation> latestByActivity = new LinkedHashMap<>();
        for (EvmCalculation e : evmRepository.findByProjectIdOrderByDataDateDesc(projectId)) {
            UUID aid = e.getActivityId();
            if (aid != null) latestByActivity.putIfAbsent(aid, e); // first seen = latest (ordered desc)
        }
        int ahead = 0, onTrack = 0, delayed = 0;
        List<Lag> laggards = new ArrayList<>();
        for (Map.Entry<UUID, EvmCalculation> en : latestByActivity.entrySet()) {
            EvmCalculation e = en.getValue();
            double pv = dbl(e.getPlannedValue());
            if (pv <= 0) continue; // no per-activity plan yet — dormant
            double ev = dbl(e.getEarnedValue());
            double spi = e.getSchedulePerformanceIndex() != null ? e.getSchedulePerformanceIndex() : ev / pv;
            if (spi < 0.90) {
                delayed++;
                laggards.add(new Lag(en.getKey(), nameById.getOrDefault(en.getKey(), "activity"), spi, ev, pv));
            } else if (spi > 1.10) {
                ahead++;
            } else {
                onTrack++;
            }
        }
        int scheduled = ahead + onTrack + delayed;
        snapshot.put("activitiesScheduled", scheduled);
        snapshot.put("activitiesAhead", ahead);
        snapshot.put("activitiesOnTrack", onTrack);
        snapshot.put("activitiesDelayed", delayed);
        if (scheduled > 0) {
            candidates.add(activityBreakdown(projectId, scheduled, ahead, onTrack, delayed, laggards, validUntil));
        }

        // --- Milestones past their planned date and not complete ---
        List<Mile> atRisk = new ArrayList<>();
        for (Activity a : activities) {
            ActivityType t = a.getActivityType();
            if (t != ActivityType.START_MILESTONE && t != ActivityType.FINISH_MILESTONE) continue;
            LocalDate due = a.getPlannedFinishDate() != null ? a.getPlannedFinishDate() : a.getPlannedStartDate();
            boolean complete = a.getActualFinishDate() != null
                    || (a.getPercentComplete() != null && a.getPercentComplete() >= 100.0);
            if (due != null && !complete && due.isBefore(today)) {
                atRisk.add(new Mile(a, ChronoUnit.DAYS.between(due, today)));
            }
        }
        snapshot.put("milestonesAtRisk", atRisk.size());
        if (!atRisk.isEmpty()) {
            candidates.add(milestoneAtRisk(projectId, atRisk, validUntil));
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft scheduleVariance(UUID projectId, double spi, double sv, double plannedPct,
                                               double earnedPct, LocalDate dataDate, EvidenceRef spiSeries,
                                               Instant validUntil) {
        Severity severity;
        String posture;
        if (spi < 0.80) {
            severity = Severity.HIGH;
            posture = "well behind";
        } else if (spi < 0.95) {
            severity = Severity.MEDIUM;
            posture = "behind";
        } else if (spi <= 1.05) {
            severity = Severity.INFO;
            posture = "on";
        } else {
            severity = Severity.INFO;
            posture = "ahead of";
        }
        long behindPP = Math.round(plannedPct - earnedPct);
        String asOf = dataDate != null ? " as of " + dataDate : "";

        return new AgentFindingDraft(
                "SCHEDULE_PROGRESS_VARIANCE", "PROJECT", severity, 0.9,
                "Project earned value vs planned value (SPI = EV/PV)" + asOf,
                "Project is " + posture + " schedule — SPI " + f2(spi) + " (" + pct(earnedPct)
                        + " earned vs " + pct(plannedPct) + " planned)",
                "The plan calls for " + pct(plannedPct) + " of the budgeted work to be complete by now, but only "
                        + pct(earnedPct) + " has actually been earned — a schedule performance index of " + f2(spi)
                        + " (schedule variance " + signed(sv) + ").",
                spi < 0.95
                        ? "Actual output is being achieved slower than the plan assumed — the fronts driving progress "
                        + "are under-resourced, blocked, or sequenced behind where the schedule expects them."
                        : "Actual output is tracking the planned rate of progress.",
                spi < 0.95
                        ? "An SPI of " + f2(spi) + " means roughly " + Math.round((1 - spi) * 100) + "% of the "
                        + "planned schedule progress is unrealised; left uncorrected it pushes the completion date "
                        + "out and concentrates recovery cost (overtime, extra crews) into the remaining duration."
                        : "Holding this pace protects the milestone dates and the forecast completion.",
                spi < 0.95
                        ? "Drive recovery on the critical fronts: add or re-balance crews, clear material/access "
                        + "blockers, and re-baseline only if the date is genuinely unrecoverable — then escalate a "
                        + "schedule-change request."
                        : "Maintain the current pace and watch near-critical fronts for early slippage.",
                scheduleVarianceEvidence(projectId, spi, plannedPct, earnedPct, behindPP, sv, spiSeries),
                Map.of("PLANNING_ENGINEER", List.of(), "PROJECT_MANAGER", List.of()), validUntil);
    }

    /** SPI metrics + EVM deep-link, led by the SPI trend chart when history is available. */
    private List<EvidenceRef> scheduleVarianceEvidence(UUID projectId, double spi, double plannedPct,
                                                       double earnedPct, long behindPP, double sv,
                                                       EvidenceRef spiSeries) {
        List<EvidenceRef> ev = new ArrayList<>();
        if (spiSeries != null) ev.add(spiSeries);
        ev.add(EvidenceRef.metric("SPI (EV/PV)", f2(spi)));
        ev.add(EvidenceRef.metric("Planned progress", pct(plannedPct)));
        ev.add(EvidenceRef.metric("Earned progress", pct(earnedPct)));
        ev.add(EvidenceRef.metric("Behind plan by", behindPP + " pts of budget"));
        ev.add(EvidenceRef.money("Schedule variance", BigDecimal.valueOf(sv)));
        ev.add(EvidenceRef.entity("EVM dashboard", "Open", "project", projectId,
                "/projects/" + projectId + "/evm"));
        return ev;
    }

    /** Project SPI over recent periods from the canonical performance rollup — the same per-period SPI the
     *  EVM tab's history chart uses. LINE chart with 1.00 as the target reference line. */
    private EvidenceRef spiHistorySeries(UUID projectId) {
        List<PeriodPerformanceRollupDto> rows;
        try {
            rows = performanceRollupService.rollup(projectId, "M");
        } catch (Exception ex) {
            return null;
        }
        List<EvidenceRef.Series.Point> pts = new ArrayList<>();
        for (PeriodPerformanceRollupDto r : rows) {
            if (r.spi() == null) continue;
            pts.add(new EvidenceRef.Series.Point(r.periodName(), Math.round(r.spi().doubleValue() * 100) / 100.0));
        }
        if (pts.size() < 2) return null;
        // Keep the last ~8 periods for a readable card chart (rollup is chronological ascending).
        if (pts.size() > 8) pts = new ArrayList<>(pts.subList(pts.size() - 8, pts.size()));
        return EvidenceRef.chart("SPI trend", new EvidenceRef.Series("LINE", "", pts, 1.0, "1.00 target"));
    }

    private AgentFindingDraft activityBreakdown(UUID projectId, int scheduled, int ahead, int onTrack,
                                                int delayed, List<Lag> laggards, Instant validUntil) {
        double delayedShare = scheduled == 0 ? 0 : (double) delayed / scheduled;
        double worstSpi = laggards.stream().mapToDouble(l -> l.spi).min().orElse(1.0);
        Severity severity;
        if (delayed == 0) severity = Severity.INFO;
        else if (delayedShare >= 0.40 || worstSpi < 0.5) severity = Severity.HIGH;
        else if (delayedShare >= 0.20 || delayed >= 3) severity = Severity.MEDIUM;
        else severity = Severity.LOW;

        laggards.sort(Comparator.comparingDouble(l -> l.spi));

        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Scheduled activities", String.valueOf(scheduled)));
        ev.add(EvidenceRef.metric("Delayed", delayed + " (" + pct(delayedShare * 100) + ")"));
        ev.add(EvidenceRef.metric("On track", String.valueOf(onTrack)));
        ev.add(EvidenceRef.metric("Ahead", String.valueOf(ahead)));
        for (Lag l : laggards.subList(0, Math.min(MAX_EXAMPLES, laggards.size()))) {
            ev.add(EvidenceRef.entity(l.name,
                    "SPI " + f2(l.spi),
                    "activity", l.activityId,
                    "/projects/" + projectId + "/activities/" + l.activityId));
        }

        String headline = delayed == 0
                ? "All " + scheduled + " scheduled activities on or ahead of plan"
                : delayed + " of " + scheduled + " scheduled activities are behind plan";

        return new AgentFindingDraft(
                "ACTIVITY_PROGRESS_VARIANCE", "PROJECT", severity, 0.85,
                "Per-activity earned vs planned value (SPI); activities without a time-phased plan are excluded",
                headline,
                "Of " + scheduled + " activities with a time-phased plan, " + delayed + " are behind (SPI < 0.90), "
                        + onTrack + " on track and " + ahead + " ahead (SPI > 1.10).",
                delayed == 0
                        ? "The active fronts are earning value at or above the planned rate."
                        : "The behind-plan activities are earning value slower than the schedule assumed — the "
                        + "specific fronts driving the project-level slip.",
                delayed == 0
                        ? "Sustained on-plan execution protects the milestone and completion dates."
                        : "These activities are where the project-level schedule variance is being generated; "
                        + "recovering them is the most direct way to lift the overall SPI.",
                delayed == 0
                        ? "Maintain pace; watch near-critical fronts for early slippage."
                        : "Focus recovery on the lowest-SPI activities: add or re-balance crews, clear "
                        + "material/access blockers, and resequence float — prioritising any on the critical path.",
                ev, Map.of("PLANNING_ENGINEER", List.of(), "PROJECT_MANAGER", List.of()), validUntil);
    }

    private AgentFindingDraft milestoneAtRisk(UUID projectId, List<Mile> milestones, Instant validUntil) {
        milestones.sort(Comparator.comparingLong((Mile m) -> m.daysLate).reversed());
        int n = milestones.size();
        long maxLate = milestones.get(0).daysLate;
        Severity severity = (maxLate >= 30 || n >= 3) ? Severity.HIGH
                : (maxLate >= 7 || n >= 2) ? Severity.MEDIUM : Severity.LOW;

        List<EvidenceRef> ev = new ArrayList<>();
        ev.add(EvidenceRef.metric("Milestones at risk", String.valueOf(n)));
        ev.add(EvidenceRef.metric("Worst overdue", maxLate + " days"));
        for (Mile m : milestones.subList(0, Math.min(MAX_EXAMPLES, n))) {
            ev.add(EvidenceRef.entity(activityLabel(m.a),
                    "due " + m.a.getPlannedFinishDate() + " · " + m.daysLate + "d overdue",
                    "activity", m.a.getId(),
                    "/projects/" + projectId + "/activities/" + m.a.getId()));
        }
        return new AgentFindingDraft(
                "MILESTONE_AT_RISK", "PROJECT", severity, 0.9,
                "Milestones whose planned date has passed while still open",
                n + " milestone" + (n == 1 ? "" : "s") + " overdue (worst " + maxLate + " days)",
                n + " project milestone" + (n == 1 ? " has" : "s have") + " passed their planned date "
                        + "without being marked complete; the most overdue is " + maxLate + " days late.",
                "The activities feeding these milestones have not finished on plan — upstream delay has reached a "
                        + "committed schedule checkpoint.",
                "Missed milestones are the visible, contractual face of schedule slip — they drive liquidated "
                        + "damages, stage-payment delays and client-reported status, so they escalate fastest.",
                "Confirm each milestone's true status; where genuinely late, replan the feeding activities and raise "
                        + "a schedule-change/mitigation plan before the slip compounds downstream.",
                ev, Map.of("PROJECT_MANAGER", List.of(), "PLANNING_ENGINEER", List.of()), validUntil);
    }

    private static String activityLabel(Activity a) {
        String name = a.getName() != null ? a.getName() : (a.getCode() != null ? a.getCode() : "milestone");
        if (name.length() > 48) name = name.substring(0, 47) + "…";
        return name;
    }

    private static double dbl(BigDecimal v) {
        return v == null ? 0.0 : v.doubleValue();
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v);
    }

    private static String f2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** Signed compact money for schedule variance (currency-neutral raw number). */
    private static String signed(double v) {
        String s = Math.abs(v) >= 1e6 ? String.format(Locale.ROOT, "%.1fM", Math.abs(v) / 1e6)
                : Math.abs(v) >= 1e3 ? String.format(Locale.ROOT, "%.0fK", Math.abs(v) / 1e3)
                : String.format(Locale.ROOT, "%.0f", Math.abs(v));
        return (v < 0 ? "-" : "+") + s;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record Mile(Activity a, long daysLate) {
    }

    private record Lag(UUID activityId, String name, double spi, double ev, double pv) {
    }
}
