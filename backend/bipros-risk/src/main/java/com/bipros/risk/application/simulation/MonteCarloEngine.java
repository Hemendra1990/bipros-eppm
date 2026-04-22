package com.bipros.risk.application.simulation;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.RelationshipType;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineActivity;
import com.bipros.baseline.infrastructure.repository.BaselineActivityRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRepository;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.risk.domain.model.ActivityCorrelation;
import com.bipros.risk.domain.model.MonteCarloActivityStat;
import com.bipros.risk.domain.model.MonteCarloCashflowBucket;
import com.bipros.risk.domain.model.MonteCarloMilestoneStat;
import com.bipros.risk.domain.model.MonteCarloRiskContribution;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskProbability;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.repository.ActivityCorrelationRepository;
import com.bipros.risk.domain.repository.RiskRepository;
import com.bipros.scheduling.domain.algorithm.CPMScheduler;
import com.bipros.scheduling.domain.algorithm.CalendarCalculator;
import com.bipros.scheduling.domain.algorithm.SchedulableActivity;
import com.bipros.scheduling.domain.algorithm.SchedulableRelationship;
import com.bipros.scheduling.domain.algorithm.ScheduleData;
import com.bipros.scheduling.domain.algorithm.ScheduledActivity;
import com.bipros.scheduling.domain.model.PertEstimate;
import com.bipros.scheduling.domain.model.SchedulingOption;
import com.bipros.scheduling.domain.repository.PertEstimateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Real PRA-style Monte Carlo engine. Per iteration:
 *  - samples every activity's duration from its configured distribution,
 *  - runs {@link CPMScheduler} over the project's network,
 *  - records project duration, project cost, per-activity duration, and critical-path membership.
 * After all iterations, aggregates percentiles, mean/stddev and per-activity stats
 * (criticality index, duration sensitivity via Pearson correlation).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MonteCarloEngine {

    private final BaselineRepository baselineRepository;
    private final BaselineActivityRepository baselineActivityRepository;
    private final ActivityRepository activityRepository;
    private final ActivityRelationshipRepository relationshipRepository;
    private final PertEstimateRepository pertEstimateRepository;
    private final RiskRepository riskRepository;
    private final ActivityCorrelationRepository activityCorrelationRepository;
    private final CalendarCalculator calendarCalculator;

    public EngineResult run(MonteCarloInput input) {
        long startNs = System.nanoTime();

        Baseline baseline = resolveActiveBaseline(input.projectId());
        List<BaselineActivity> baselineActivities = baselineActivityRepository.findByBaselineId(baseline.getId());
        if (baselineActivities.isEmpty()) {
            throw new BusinessRuleException("BASELINE_EMPTY",
                "Active baseline has no activity snapshot; re-capture the baseline before running Monte Carlo.");
        }

        List<Activity> activities = activityRepository.findByProjectId(input.projectId());
        if (activities.isEmpty()) {
            throw new BusinessRuleException("NO_ACTIVITIES",
                "Project has no activities; cannot run Monte Carlo simulation.");
        }
        List<ActivityRelationship> relationships = relationshipRepository.findByProjectId(input.projectId());

        Map<UUID, Activity> activityById = new HashMap<>();
        for (Activity a : activities) activityById.put(a.getId(), a);

        Map<UUID, BaselineActivity> baselineByActivity = new HashMap<>();
        for (BaselineActivity ba : baselineActivities) baselineByActivity.put(ba.getActivityId(), ba);

        List<UUID> activityIds = activities.stream().map(Activity::getId).toList();
        Map<UUID, PertEstimate> pertById = new HashMap<>();
        for (PertEstimate pe : pertEstimateRepository.findByActivityIdIn(activityIds)) {
            pertById.put(pe.getActivityId(), pe);
        }

        UUID defaultCalendarId = activities.stream()
            .map(Activity::getCalendarId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new BusinessRuleException("NO_CALENDAR",
                "No calendar assigned to any activity; assign calendars before running Monte Carlo."));

        LocalDate projectStartDate = Optional.ofNullable(baseline.getProjectStartDate())
            .orElseGet(() -> activities.stream()
                .map(Activity::getPlannedStartDate).filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElseThrow(() -> new BusinessRuleException("NO_START_DATE",
                    "Cannot determine project start date.")));
        LocalDate dataDate = Optional.ofNullable(baseline.getBaselineDate()).orElse(projectStartDate);

        // Per-activity baseline cost. Fall back to proportional allocation of Baseline.totalCost
        // by originalDuration if some activities lack a plannedCost snapshot.
        Map<UUID, BigDecimal> activityBaselineCost = buildBaselineCostMap(activities, baseline, baselineByActivity);

        // Per-activity sampler. PERT row → Triangular(O,M,P); else fallback band around originalDuration.
        Map<UUID, DistributionSampler> samplers = new HashMap<>();
        Map<UUID, Double> plannedDuration = new HashMap<>();
        for (Activity a : activities) {
            double planned = Optional.ofNullable(a.getOriginalDuration()).orElse(0.0);
            plannedDuration.put(a.getId(), planned);
            PertEstimate pe = pertById.get(a.getId());
            DistributionSampler sampler;
            if (pe != null && pe.getOptimisticDuration() != null
                && pe.getMostLikelyDuration() != null && pe.getPessimisticDuration() != null
                && pe.getPessimisticDuration() > pe.getOptimisticDuration()) {
                sampler = DistributionSamplers.threePoint(input.defaultDistribution(),
                    pe.getOptimisticDuration(), pe.getMostLikelyDuration(), pe.getPessimisticDuration());
            } else if (planned > 0.0) {
                sampler = DistributionSamplers.fallback(planned, input.fallbackVariancePct(),
                    input.defaultDistribution());
            } else {
                sampler = new ConstantSampler(0.0);
            }
            samplers.put(a.getId(), sampler);
        }

        // Size the calendar bitmap horizon to cover worst-case iteration duration.
        // Planned duration × (1 + max variance) plus generous slack. Using 5× planned is safe
        // and cheap (e.g. a 10-year project → 50-year bitmap ≈ 18k days × ~2 bytes).
        double plannedSum = activities.stream()
            .mapToDouble(a -> Optional.ofNullable(a.getOriginalDuration()).orElse(0.0))
            .sum();
        double baselineProjectDuration = Optional.ofNullable(baseline.getProjectDuration()).orElse(plannedSum);
        int horizonDays = (int) Math.max(365, baselineProjectDuration * 5);
        CachingCalendarCalculator cachingCalendar =
            new CachingCalendarCalculator(calendarCalculator, projectStartDate, horizonDays);
        // Pre-warm every distinct calendar referenced by the project's activities.
        activities.stream()
            .map(Activity::getCalendarId).filter(Objects::nonNull).distinct()
            .forEach(cachingCalendar::primeHorizon);
        cachingCalendar.primeHorizon(defaultCalendarId);

        // Precompute schedulable relationships (unchanging across iterations).
        List<SchedulableRelationship> schedulableRelationships = new ArrayList<>(relationships.size());
        for (ActivityRelationship rel : relationships) {
            schedulableRelationships.add(new SchedulableRelationship(
                rel.getPredecessorActivityId(),
                rel.getSuccessorActivityId(),
                shortCode(rel.getRelationshipType()),
                rel.getLag() != null ? rel.getLag() : 0.0));
        }

        // Compute the deterministic baseline duration = CPM over originalDuration (no sampling).
        // This gives an apples-to-apples comparison with simulated durations. We override the
        // Baseline entity's stored projectDuration field (which is typically the sum of durations
        // or a manually-set value) with this derived value.
        double deterministicBaselineDuration = runDeterministicCpm(
            activities, schedulableRelationships, projectStartDate, dataDate, defaultCalendarId, cachingCalendar);

        int n = input.iterations();
        int aN = activities.size();
        double[] iterDurations = new double[n];
        BigDecimal[] iterCosts = new BigDecimal[n];
        int[] criticalHits = new int[aN];
        double[][] activityDurations = new double[aN][n];
        double[][] activityCostIter = new double[aN][n];
        int[][] earlyStartEpoch = new int[aN][n];
        int[][] earlyFinishEpoch = new int[aN][n];
        UUID[] orderedIds = activityIds.toArray(new UUID[0]);
        Map<UUID, Integer> indexOf = new HashMap<>();
        for (int i = 0; i < orderedIds.length; i++) indexOf.put(orderedIds[i], i);

        // Milestones: FINISH_MILESTONE activities (and START_MILESTONE which are always day-0).
        List<Integer> milestoneIdx = new ArrayList<>();
        for (int i = 0; i < aN; i++) {
            Activity a = activityById.get(orderedIds[i]);
            if (a == null || a.getActivityType() == null) continue;
            String t = a.getActivityType().name();
            if ("FINISH_MILESTONE".equals(t) || "START_MILESTONE".equals(t)) milestoneIdx.add(i);
        }
        int[][] milestoneFinishEpoch = new int[milestoneIdx.size()][n];

        // Cashflow monthly buckets: month-end dates from projectStart to projectStart + horizon.
        List<LocalDate> bucketEnds = new ArrayList<>();
        {
            LocalDate cur = projectStartDate.with(TemporalAdjusters.lastDayOfMonth());
            LocalDate horizonEnd = projectStartDate.plusDays(horizonDays);
            while (!cur.isAfter(horizonEnd)) {
                bucketEnds.add(cur);
                cur = cur.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
            }
        }
        int bN = bucketEnds.size();
        int[] bucketEndEpoch = new int[bN];
        for (int b = 0; b < bN; b++) bucketEndEpoch[b] = (int) bucketEnds.get(b).toEpochDay();
        double[][] iterBucketCost = new double[n][bN];

        long seed = input.randomSeed() != null ? input.randomSeed() : System.nanoTime();
        SplittableRandom masterRng = new SplittableRandom(seed);

        // Correlation reshuffling: precompute an N × aN uniform matrix where each column's
        // rank correlation with other columns follows the target matrix. For uncorrelated
        // activities (no rows mentioning them), the matrix is effectively independent uniforms.
        double[][] correlatedU = buildCorrelatedUniforms(input.projectId(), orderedIds, n, masterRng.nextLong());

        // Risk drivers derived from the project's risk register.
        List<RiskDriver> drivers = input.enableRisks()
            ? buildRiskDrivers(input.projectId(), activityById)
            : List.of();
        int[] riskOccurrences = new int[drivers.size()];
        double[] riskTotalDuration = new double[drivers.size()];
        double[] riskTotalCost = new double[drivers.size()];

        for (int iter = 0; iter < n; iter++) {
            RandomGenerator iterRng = masterRng.split();

            Map<UUID, Double> sampled = new HashMap<>(activities.size() * 2);
            for (int ai = 0; ai < aN; ai++) {
                UUID id = orderedIds[ai];
                double u = correlatedU[iter][ai];
                sampled.put(id, Math.max(0.0, samplers.get(id).sampleFromUniform(u)));
            }

            // Apply risk drivers: for each risk, Bernoulli-sample occurrence. If it occurs,
            // sample the (triangular) schedule-impact range and add that many days to each
            // affected activity; sample the cost-impact range and add to the iteration cost.
            double iterRiskCost = 0.0;
            for (int dIdx = 0; dIdx < drivers.size(); dIdx++) {
                RiskDriver drv = drivers.get(dIdx);
                if (iterRng.nextDouble() >= drv.probability()) continue;
                double scheduleDays = drv.scheduleImpactSampler().sample(iterRng);
                double costImpact = drv.costImpactSampler().sample(iterRng);
                for (UUID affected : drv.affectedActivityIds()) {
                    if (sampled.containsKey(affected)) {
                        sampled.merge(affected, scheduleDays, Double::sum);
                    }
                }
                iterRiskCost += costImpact;
                riskOccurrences[dIdx]++;
                riskTotalDuration[dIdx] += scheduleDays;
                riskTotalCost[dIdx] += costImpact;
            }

            List<SchedulableActivity> schedulableActivities = new ArrayList<>(activities.size());
            for (Activity a : activities) {
                double dur = sampled.get(a.getId());
                schedulableActivities.add(new SchedulableActivity(
                    a.getId(),
                    dur,
                    dur,
                    a.getCalendarId() != null ? a.getCalendarId() : defaultCalendarId,
                    a.getActivityType() != null ? a.getActivityType().name() : null,
                    "NOT_STARTED",
                    0.0,
                    null,
                    null,
                    a.getPrimaryConstraintType() != null ? a.getPrimaryConstraintType().name() : null,
                    null,
                    null,
                    null
                ));
            }

            ScheduleData scheduleData = new ScheduleData(
                input.projectId(),
                dataDate,
                projectStartDate,
                null,
                schedulableActivities,
                schedulableRelationships,
                SchedulingOption.RETAINED_LOGIC);

            CPMScheduler scheduler = new CPMScheduler(cachingCalendar, defaultCalendarId);
            CPMScheduler.ScheduleOutput out;
            try {
                out = scheduler.scheduleWithWarnings(scheduleData);
            } catch (RuntimeException ex) {
                throw new BusinessRuleException("CPM_FAILED",
                    "CPM failed during iteration " + iter + ": " + ex.getMessage());
            }

            LocalDate projectFinish = out.activities().stream()
                .map(ScheduledActivity::getEarlyFinish)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(projectStartDate);

            double projectDuration = cachingCalendar.countWorkingDays(
                defaultCalendarId, projectStartDate, projectFinish);
            iterDurations[iter] = projectDuration;

            // Index scheduled activities for quick lookup in the cost + milestone + cashflow pass.
            Map<UUID, ScheduledActivity> byId = new HashMap<>(out.activities().size() * 2);
            for (ScheduledActivity sa : out.activities()) byId.put(sa.getActivityId(), sa);

            // Cost = Σ (baseline_cost_i × sampled_dur_i / planned_dur_i); also accrue per bucket.
            BigDecimal iterCost = BigDecimal.ZERO;
            double[] bucketRow = iterBucketCost[iter];
            for (int i = 0; i < aN; i++) {
                UUID id = orderedIds[i];
                Activity a = activityById.get(id);
                ScheduledActivity sa = byId.get(id);
                double dur = sampled.getOrDefault(id, 0.0);
                activityDurations[i][iter] = dur;
                if (sa != null) {
                    if (sa.isCritical()) criticalHits[i]++;
                    LocalDate es = sa.getEarlyStart();
                    LocalDate ef = sa.getEarlyFinish();
                    earlyStartEpoch[i][iter] = es != null ? (int) es.toEpochDay() : 0;
                    earlyFinishEpoch[i][iter] = ef != null ? (int) ef.toEpochDay() : 0;
                }

                BigDecimal baseCost = activityBaselineCost.getOrDefault(id, BigDecimal.ZERO);
                double activityCost = 0.0;
                if (baseCost.signum() > 0) {
                    double planned = plannedDuration.get(id);
                    if (planned > 0.0) {
                        activityCost = baseCost.doubleValue() * (dur / planned);
                    } else {
                        activityCost = baseCost.doubleValue();
                    }
                    iterCost = iterCost.add(BigDecimal.valueOf(activityCost));
                }
                activityCostIter[i][iter] = activityCost;

                // Accrue into monthly buckets via linear-in-time contribution.
                if (activityCost > 0.0 && sa != null && sa.getEarlyStart() != null && sa.getEarlyFinish() != null) {
                    int startE = earlyStartEpoch[i][iter];
                    int finishE = earlyFinishEpoch[i][iter];
                    int span = Math.max(1, finishE - startE);
                    for (int b = 0; b < bN; b++) {
                        int bEnd = bucketEndEpoch[b];
                        double frac;
                        if (bEnd <= startE) frac = 0.0;
                        else if (bEnd >= finishE) frac = 1.0;
                        else frac = (double) (bEnd - startE) / span;
                        bucketRow[b] += activityCost * frac;
                    }
                }
            }
            if (iterRiskCost > 0) {
                iterCost = iterCost.add(BigDecimal.valueOf(iterRiskCost));
            }
            iterCosts[iter] = iterCost;

            // Record milestone finish dates for this iteration.
            for (int m = 0; m < milestoneIdx.size(); m++) {
                int idx = milestoneIdx.get(m);
                milestoneFinishEpoch[m][iter] = earlyFinishEpoch[idx][iter];
            }
        }

        // Aggregates.
        double[] sortedDur = iterDurations.clone();
        Arrays.sort(sortedDur);
        BigDecimal[] sortedCost = iterCosts.clone();
        Arrays.sort(sortedCost, BigDecimal::compareTo);

        PercentileSnapshot durStats = PercentileSnapshot.ofDouble(sortedDur);
        CostSnapshot costStats = CostSnapshot.of(sortedCost);

        // Per-activity stats.
        PearsonsCorrelation pearson = new PearsonsCorrelation();
        List<MonteCarloActivityStat> activityStats = new ArrayList<>(activities.size());
        for (int i = 0; i < orderedIds.length; i++) {
            double[] series = activityDurations[i];
            double mean = mean(series);
            double sd = stddev(series, mean);
            double[] sortedSeries = series.clone();
            Arrays.sort(sortedSeries);
            double p10 = percentile(sortedSeries, 10);
            double p90 = percentile(sortedSeries, 90);
            double durSens;
            try {
                durSens = (sd > 1e-9) ? pearson.correlation(series, iterDurations) : 0.0;
            } catch (Exception e) {
                durSens = 0.0;
            }
            double criticality = (double) criticalHits[i] / n;
            Activity a = activityById.get(orderedIds[i]);
            activityStats.add(MonteCarloActivityStat.builder()
                .activityId(orderedIds[i])
                .activityCode(a != null ? a.getCode() : null)
                .activityName(a != null ? a.getName() : null)
                .criticalityIndex(criticality)
                .durationMean(mean)
                .durationStddev(sd)
                .durationP10(p10)
                .durationP90(p90)
                .durationSensitivity(durSens)
                .costSensitivity(durSens) // identical under Phase 1's duration-driven cost model
                .cruciality(criticality * Math.abs(durSens))
                .build());
        }

        // Milestone stats: for each milestone, sort its n finish-date epochs and derive percentiles + CDF.
        List<MonteCarloMilestoneStat> milestoneStats = new ArrayList<>(milestoneIdx.size());
        for (int m = 0; m < milestoneIdx.size(); m++) {
            int idx = milestoneIdx.get(m);
            Activity a = activityById.get(orderedIds[idx]);
            int[] finishes = milestoneFinishEpoch[m].clone();
            Arrays.sort(finishes);
            LocalDate p50 = LocalDate.ofEpochDay(finishes[(int) Math.min(n - 1, Math.round(0.5 * (n - 1)))]);
            LocalDate p80 = LocalDate.ofEpochDay(finishes[(int) Math.min(n - 1, Math.round(0.8 * (n - 1)))]);
            LocalDate p90 = LocalDate.ofEpochDay(finishes[(int) Math.min(n - 1, Math.round(0.9 * (n - 1)))]);
            String cdfJson = buildCdfJson(finishes);
            LocalDate planned = a != null ? a.getPlannedFinishDate() : null;
            milestoneStats.add(MonteCarloMilestoneStat.builder()
                .activityId(orderedIds[idx])
                .activityCode(a != null ? a.getCode() : null)
                .activityName(a != null ? a.getName() : null)
                .plannedFinishDate(planned)
                .p50FinishDate(p50)
                .p80FinishDate(p80)
                .p90FinishDate(p90)
                .cdfJson(cdfJson)
                .build());
        }

        // Cashflow buckets: for each bucket, sort across iterations and read P10/P50/P80/P90.
        List<MonteCarloCashflowBucket> cashflow = new ArrayList<>(bN);
        double[] bucketCol = new double[n];
        for (int b = 0; b < bN; b++) {
            for (int it = 0; it < n; it++) bucketCol[it] = iterBucketCost[it][b];
            Arrays.sort(bucketCol);
            BigDecimal p10 = BigDecimal.valueOf(bucketCol[(int) Math.round(0.10 * (n - 1))]).setScale(2, RoundingMode.HALF_UP);
            BigDecimal p50 = BigDecimal.valueOf(bucketCol[(int) Math.round(0.50 * (n - 1))]).setScale(2, RoundingMode.HALF_UP);
            BigDecimal p80 = BigDecimal.valueOf(bucketCol[(int) Math.round(0.80 * (n - 1))]).setScale(2, RoundingMode.HALF_UP);
            BigDecimal p90 = BigDecimal.valueOf(bucketCol[(int) Math.round(0.90 * (n - 1))]).setScale(2, RoundingMode.HALF_UP);
            cashflow.add(MonteCarloCashflowBucket.builder()
                .periodEndDate(bucketEnds.get(b))
                .p10Cumulative(p10)
                .p50Cumulative(p50)
                .p80Cumulative(p80)
                .p90Cumulative(p90)
                .build());
        }

        // Risk contributions: one row per driver that was eligible (even if never fired).
        List<MonteCarloRiskContribution> contributions = new ArrayList<>(drivers.size());
        for (int dIdx = 0; dIdx < drivers.size(); dIdx++) {
            RiskDriver drv = drivers.get(dIdx);
            int occ = riskOccurrences[dIdx];
            double rate = (double) occ / Math.max(1, n);
            double meanDur = occ > 0 ? riskTotalDuration[dIdx] / occ : 0.0;
            BigDecimal meanCost = occ > 0
                ? BigDecimal.valueOf(riskTotalCost[dIdx] / occ).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            contributions.add(MonteCarloRiskContribution.builder()
                .riskId(drv.riskId())
                .riskCode(drv.riskCode())
                .riskTitle(drv.riskTitle())
                .occurrences(occ)
                .occurrenceRate(rate)
                .meanDurationImpact(meanDur)
                .meanCostImpact(meanCost)
                .affectedActivityIds(drv.affectedActivityIdsText())
                .build());
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("Monte Carlo engine completed {} iterations in {} ms (project={}, baseline={}, milestones={}, buckets={}, drivers={})",
            n, elapsedMs, input.projectId(), baseline.getId(), milestoneStats.size(), cashflow.size(), drivers.size());

        return new EngineResult(
            baseline.getId(),
            deterministicBaselineDuration,
            baseline.getTotalCost() != null ? baseline.getTotalCost() : BigDecimal.ZERO,
            dataDate,
            durStats,
            costStats,
            iterDurations,
            iterCosts,
            activityStats,
            milestoneStats,
            cashflow,
            contributions);
    }

    /**
     * Build a RiskDriver for every non-closed Risk in the project with probability > 0, at least one
     * resolvable affected activity, and a non-zero schedule or cost impact. Uses the Risk enum-probability
     * mapping ({@link #toProbability}) and wraps the single-point scheduleImpactDays / costImpact as
     * triangular ranges (±20%) for stochastic draw.
     */
    private List<RiskDriver> buildRiskDrivers(UUID projectId, Map<UUID, Activity> activityById) {
        List<Risk> risks = riskRepository.findByProjectId(projectId);
        List<RiskDriver> out = new ArrayList<>();
        for (Risk r : risks) {
            if (r.getStatus() == RiskStatus.CLOSED || r.getStatus() == RiskStatus.RESOLVED) continue;
            double probability = toProbability(r.getProbability());
            if (probability <= 0) continue;

            List<UUID> affected = parseAffectedActivities(r.getAffectedActivities(), activityById);
            if (affected.isEmpty()) continue;

            double impactDays = r.getScheduleImpactDays() != null ? r.getScheduleImpactDays() : 0.0;
            BigDecimal impactCost = Optional.ofNullable(r.getCostImpact()).orElse(BigDecimal.ZERO);
            if (impactDays == 0.0 && impactCost.signum() == 0) continue;

            DistributionSampler durSampler = impactDays > 0
                ? new TriangularSampler(impactDays * 0.8, impactDays, impactDays * 1.2)
                : new ConstantSampler(0.0);
            DistributionSampler costSampler = impactCost.signum() > 0
                ? new TriangularSampler(impactCost.doubleValue() * 0.8, impactCost.doubleValue(), impactCost.doubleValue() * 1.2)
                : new ConstantSampler(0.0);

            out.add(new RiskDriver(r.getId(), r.getCode(), r.getTitle(), probability, affected,
                durSampler, costSampler));
        }
        return out;
    }

    private static double toProbability(RiskProbability rp) {
        if (rp == null) return 0.0;
        return switch (rp) {
            case VERY_LOW -> 0.10;
            case LOW -> 0.25;
            case MEDIUM -> 0.50;
            case HIGH -> 0.75;
            case VERY_HIGH -> 0.90;
        };
    }

    /**
     * Parse Risk.affectedActivities into resolvable activity UUIDs. Supports the two formats
     * the frontend has historically used: comma-separated activity codes, or comma-separated
     * UUID strings. Unresolvable entries are skipped silently.
     */
    private static List<UUID> parseAffectedActivities(String raw, Map<UUID, Activity> activityById) {
        if (raw == null || raw.isBlank()) return List.of();
        // Build a code → id index.
        Map<String, UUID> codeIndex = new HashMap<>();
        for (Activity a : activityById.values()) {
            if (a.getCode() != null) codeIndex.put(a.getCode().trim(), a.getId());
        }
        List<UUID> out = new ArrayList<>();
        for (String token : raw.split("[,\\[\\]\\s]+")) {
            String t = token.trim();
            if (t.isEmpty() || t.equals("\"")) continue;
            t = t.replace("\"", "");
            if (t.isEmpty()) continue;
            try {
                UUID id = UUID.fromString(t);
                if (activityById.containsKey(id)) out.add(id);
                continue;
            } catch (IllegalArgumentException ignored) { /* not a UUID */ }
            UUID byCode = codeIndex.get(t);
            if (byCode != null) out.add(byCode);
        }
        return out;
    }

    /** In-memory risk driver: probability + duration/cost impact samplers + affected activity ids. */
    private record RiskDriver(
        UUID riskId, String riskCode, String riskTitle,
        double probability, List<UUID> affectedActivityIds,
        DistributionSampler scheduleImpactSampler, DistributionSampler costImpactSampler) {

        String affectedActivityIdsText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < affectedActivityIds.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(affectedActivityIds.get(i));
            }
            return sb.toString();
        }
    }

    private static String buildCdfJson(int[] sortedEpochs) {
        int n = sortedEpochs.length;
        if (n == 0) return "[]";
        StringBuilder sb = new StringBuilder().append('[');
        int samples = 20;
        for (int i = 0; i < samples; i++) {
            int idx = (int) Math.min(n - 1, Math.round((double) i / (samples - 1) * (n - 1)));
            LocalDate d = LocalDate.ofEpochDay(sortedEpochs[idx]);
            if (i > 0) sb.append(',');
            sb.append("{\"date\":\"").append(d).append("\",\"p\":")
                .append(String.format("%.4f", (double) i / (samples - 1))).append('}');
        }
        return sb.append(']').toString();
    }

    // ---- helpers ----

    private Baseline resolveActiveBaseline(UUID projectId) {
        List<Baseline> active = baselineRepository.findByProjectIdAndIsActiveTrue(projectId);
        if (active.isEmpty()) {
            throw new BusinessRuleException("BASELINE_REQUIRED",
                "Create an active baseline before running a Monte Carlo simulation.");
        }
        // If multiple are flagged active, pick the most recent.
        return active.stream()
            .max((a, b) -> {
                LocalDate aDate = a.getBaselineDate();
                LocalDate bDate = b.getBaselineDate();
                if (aDate == null && bDate == null) return 0;
                if (aDate == null) return -1;
                if (bDate == null) return 1;
                return aDate.compareTo(bDate);
            })
            .orElse(active.get(0));
    }

    private Map<UUID, BigDecimal> buildBaselineCostMap(List<Activity> activities, Baseline baseline,
                                                      Map<UUID, BaselineActivity> baselineByActivity) {
        Map<UUID, BigDecimal> costs = new HashMap<>();
        boolean anyExplicit = false;
        BigDecimal explicitTotal = BigDecimal.ZERO;
        for (Activity a : activities) {
            BaselineActivity ba = baselineByActivity.get(a.getId());
            BigDecimal c = ba != null ? ba.getPlannedCost() : null;
            if (c != null && c.signum() > 0) {
                costs.put(a.getId(), c);
                explicitTotal = explicitTotal.add(c);
                anyExplicit = true;
            }
        }
        // Fallback: proportionally allocate baseline.totalCost to activities lacking plannedCost,
        // weighted by originalDuration. If no activity has an explicit cost, allocate the full
        // total across everything.
        BigDecimal remainingTotal = Optional.ofNullable(baseline.getTotalCost()).orElse(BigDecimal.ZERO)
            .subtract(explicitTotal);
        if (remainingTotal.signum() > 0) {
            double weightSum = activities.stream()
                .filter(a -> !costs.containsKey(a.getId()))
                .mapToDouble(a -> Optional.ofNullable(a.getOriginalDuration()).orElse(0.0))
                .sum();
            if (weightSum > 0) {
                for (Activity a : activities) {
                    if (costs.containsKey(a.getId())) continue;
                    double w = Optional.ofNullable(a.getOriginalDuration()).orElse(0.0) / weightSum;
                    costs.put(a.getId(), remainingTotal.multiply(BigDecimal.valueOf(w))
                        .setScale(4, RoundingMode.HALF_UP));
                }
            }
        }
        return costs;
    }

    /**
     * Build an N × aN uniform matrix whose columns satisfy the project's configured activity
     * correlations. Activities not mentioned in any correlation row behave identically to an
     * independent uniform (the corresponding matrix columns have no off-diagonal entries).
     * If the project has no correlations at all, {@link ImanConover} short-circuits to plain
     * independent sampling.
     */
    private double[][] buildCorrelatedUniforms(UUID projectId, UUID[] orderedIds, int iterations, long seed) {
        int aN = orderedIds.length;
        double[][] R = new double[aN][aN];
        for (int i = 0; i < aN; i++) R[i][i] = 1.0;

        List<ActivityCorrelation> correlations = activityCorrelationRepository.findByProjectId(projectId);
        if (!correlations.isEmpty()) {
            Map<UUID, Integer> idx = new HashMap<>(aN * 2);
            for (int i = 0; i < aN; i++) idx.put(orderedIds[i], i);

            int applied = 0;
            for (ActivityCorrelation c : correlations) {
                Integer i = idx.get(c.getActivityAId());
                Integer j = idx.get(c.getActivityBId());
                if (i == null || j == null || i.equals(j)) continue;
                double coef = Math.max(-0.99, Math.min(0.99, c.getCoefficient()));
                R[i][j] = coef;
                R[j][i] = coef;
                applied++;
            }
            log.info("Activity correlations applied: {} of {}", applied, correlations.size());
        }

        return ImanConover.correlatedUniforms(iterations, R, seed);
    }

    /** Run CPM once with each activity at its originalDuration (or 0 for milestones). */
    private double runDeterministicCpm(List<Activity> activities,
                                       List<SchedulableRelationship> relationships,
                                       LocalDate projectStartDate, LocalDate dataDate,
                                       UUID defaultCalendarId, CachingCalendarCalculator cachingCalendar) {
        List<SchedulableActivity> baselineSched = new ArrayList<>(activities.size());
        for (Activity a : activities) {
            double dur = Optional.ofNullable(a.getOriginalDuration()).orElse(0.0);
            baselineSched.add(new SchedulableActivity(
                a.getId(), dur, dur,
                a.getCalendarId() != null ? a.getCalendarId() : defaultCalendarId,
                a.getActivityType() != null ? a.getActivityType().name() : null,
                "NOT_STARTED", 0.0, null, null,
                a.getPrimaryConstraintType() != null ? a.getPrimaryConstraintType().name() : null,
                null, null, null));
        }
        ScheduleData sd = new ScheduleData(
            activities.get(0).getProjectId(), dataDate, projectStartDate, null,
            baselineSched, relationships, SchedulingOption.RETAINED_LOGIC);
        CPMScheduler.ScheduleOutput out = new CPMScheduler(cachingCalendar, defaultCalendarId)
            .scheduleWithWarnings(sd);
        LocalDate finish = out.activities().stream()
            .map(ScheduledActivity::getEarlyFinish)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(projectStartDate);
        return cachingCalendar.countWorkingDays(defaultCalendarId, projectStartDate, finish);
    }

    private static String shortCode(RelationshipType type) {
        if (type == null) return "FS";
        return switch (type) {
            case FINISH_TO_START -> "FS";
            case FINISH_TO_FINISH -> "FF";
            case START_TO_START -> "SS";
            case START_TO_FINISH -> "SF";
        };
    }

    private static double mean(double[] x) {
        double s = 0;
        for (double v : x) s += v;
        return x.length == 0 ? 0 : s / x.length;
    }

    private static double stddev(double[] x, double mean) {
        if (x.length < 2) return 0;
        double sq = 0;
        for (double v : x) { double d = v - mean; sq += d * d; }
        return Math.sqrt(sq / (x.length - 1));
    }

    private static double percentile(double[] sorted, double pct) {
        if (sorted.length == 0) return 0;
        int idx = (int) Math.min(sorted.length - 1, Math.max(0, Math.round(pct / 100.0 * (sorted.length - 1))));
        return sorted[idx];
    }

    /** Percentile bundle over a sorted double[]. */
    public record PercentileSnapshot(
        double p10, double p25, double p50, double p75, double p80, double p90, double p95, double p99,
        double mean, double stddev) {

        static PercentileSnapshot ofDouble(double[] sorted) {
            double mean = Arrays.stream(sorted).average().orElse(0);
            double var = 0;
            for (double v : sorted) { double d = v - mean; var += d * d; }
            double sd = sorted.length < 2 ? 0 : Math.sqrt(var / (sorted.length - 1));
            return new PercentileSnapshot(
                percentile(sorted, 10), percentile(sorted, 25), percentile(sorted, 50),
                percentile(sorted, 75), percentile(sorted, 80), percentile(sorted, 90),
                percentile(sorted, 95), percentile(sorted, 99),
                mean, sd);
        }
    }

    /** Percentile bundle over a sorted BigDecimal[]. */
    public record CostSnapshot(
        BigDecimal p10, BigDecimal p25, BigDecimal p50, BigDecimal p75, BigDecimal p80,
        BigDecimal p90, BigDecimal p95, BigDecimal p99,
        BigDecimal mean, BigDecimal stddev) {

        static CostSnapshot of(BigDecimal[] sorted) {
            if (sorted.length == 0) {
                BigDecimal z = BigDecimal.ZERO;
                return new CostSnapshot(z, z, z, z, z, z, z, z, z, z);
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal v : sorted) sum = sum.add(v);
            BigDecimal mean = sum.divide(BigDecimal.valueOf(sorted.length), 4, RoundingMode.HALF_UP);
            double m = mean.doubleValue();
            double var = 0;
            for (BigDecimal v : sorted) { double d = v.doubleValue() - m; var += d * d; }
            BigDecimal sd = sorted.length < 2 ? BigDecimal.ZERO
                : BigDecimal.valueOf(Math.sqrt(var / (sorted.length - 1))).setScale(4, RoundingMode.HALF_UP);
            return new CostSnapshot(
                costPercentile(sorted, 10), costPercentile(sorted, 25), costPercentile(sorted, 50),
                costPercentile(sorted, 75), costPercentile(sorted, 80), costPercentile(sorted, 90),
                costPercentile(sorted, 95), costPercentile(sorted, 99),
                mean, sd);
        }

        private static BigDecimal costPercentile(BigDecimal[] sorted, double pct) {
            int idx = (int) Math.min(sorted.length - 1,
                Math.max(0, Math.round(pct / 100.0 * (sorted.length - 1))));
            return sorted[idx];
        }
    }

    /** Full engine output: simulation aggregates + per-iteration series + derived stats. */
    public record EngineResult(
        UUID baselineId,
        Double baselineDuration,
        BigDecimal baselineCost,
        LocalDate dataDate,
        PercentileSnapshot durationStats,
        CostSnapshot costStats,
        double[] iterationDurations,
        BigDecimal[] iterationCosts,
        List<MonteCarloActivityStat> activityStats,
        List<MonteCarloMilestoneStat> milestoneStats,
        List<MonteCarloCashflowBucket> cashflow,
        List<MonteCarloRiskContribution> riskContributions
    ) {}

    /** Degenerate sampler used when an activity's planned duration is zero (e.g. milestones). */
    private static final class ConstantSampler implements DistributionSampler {
        private final double value;
        ConstantSampler(double value) { this.value = value; }
        @Override public double sample(RandomGenerator rng) { return value; }
        @Override public double mode() { return value; }
    }
}
