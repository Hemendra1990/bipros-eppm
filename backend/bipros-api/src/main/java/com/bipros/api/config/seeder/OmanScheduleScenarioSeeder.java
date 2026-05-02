package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.scheduling.domain.model.CompressionAnalysis;
import com.bipros.scheduling.domain.model.CompressionType;
import com.bipros.scheduling.domain.model.PertEstimate;
import com.bipros.scheduling.domain.model.RiskLevel;
import com.bipros.scheduling.domain.model.ScheduleActivityResult;
import com.bipros.scheduling.domain.model.ScheduleHealthIndex;
import com.bipros.scheduling.domain.model.ScheduleResult;
import com.bipros.scheduling.domain.model.ScheduleScenario;
import com.bipros.scheduling.domain.model.ScheduleStatus;
import com.bipros.scheduling.domain.model.SchedulingOption;
import com.bipros.scheduling.domain.repository.CompressionAnalysisRepository;
import com.bipros.scheduling.domain.repository.PertEstimateRepository;
import com.bipros.scheduling.domain.repository.ScheduleActivityResultRepository;
import com.bipros.scheduling.domain.repository.ScheduleHealthIndexRepository;
import com.bipros.scheduling.domain.repository.ScheduleResultRepository;
import com.bipros.scheduling.domain.repository.ScheduleScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
@Profile("seed")
@Order(149)
@RequiredArgsConstructor
public class OmanScheduleScenarioSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";
    private static final long DETERMINISTIC_SEED = 6155L;
    private static final LocalDate DEFAULT_DATA_DATE = LocalDate.of(2026, 4, 29);

    private final ProjectRepository projectRepository;
    private final ScheduleScenarioRepository scheduleScenarioRepository;
    private final ScheduleResultRepository scheduleResultRepository;
    private final ScheduleActivityResultRepository scheduleActivityResultRepository;
    private final CompressionAnalysisRepository compressionAnalysisRepository;
    private final PertEstimateRepository pertEstimateRepository;
    private final ScheduleHealthIndexRepository scheduleHealthIndexRepository;
    private final ActivityRepository activityRepository;

    @Override
    public void run(String... args) {
        Project project = projectRepository.findByCode(PROJECT_CODE).orElse(null);
        if (project == null) {
            log.warn("[BNK-SCHED] project '{}' not found — skipping", PROJECT_CODE);
            return;
        }
        UUID projectId = project.getId();

        if (!scheduleResultRepository.findByProjectId(projectId).isEmpty()) {
            log.info("[BNK-SCHED] schedule results already present for project '{}' — skipping", PROJECT_CODE);
            return;
        }

        log.info("[BNK-SCHED] seeding schedule analysis data for project '{}'", PROJECT_CODE);
        Random rng = new Random(DETERMINISTIC_SEED);

        List<Activity> activities = activityRepository.findByProjectId(projectId);
        if (activities.isEmpty()) {
            log.warn("[BNK-SCHED] no activities found — skipping schedule analysis seeding");
            return;
        }

        List<ScheduleScenario> scenarios = scheduleScenarioRepository.findByProjectId(projectId);
        ScheduleScenario baseline = scenarios.stream()
                .filter(s -> s.getScenarioType() == com.bipros.scheduling.domain.model.ScenarioType.BASELINE)
                .findFirst().orElse(scenarios.isEmpty() ? null : scenarios.get(0));

        ScheduleResult result = seedScheduleResult(projectId, baseline, activities);
        seedScheduleActivityResults(result.getId(), activities, rng);
        seedCompressionAnalyses(projectId, baseline, rng);
        seedPertEstimates(activities, rng);
        seedScheduleHealthIndex(projectId, result.getId(), activities, rng);

        log.info("[BNK-SCHED] schedule analysis seeding completed");
    }

    private ScheduleResult seedScheduleResult(UUID projectId, ScheduleScenario baseline, List<Activity> activities) {
        long criticalCount = activities.stream()
                .filter(a -> a.getTotalFloat() != null && a.getTotalFloat() <= 0)
                .count();
        if (criticalCount == 0) criticalCount = Math.max(1, activities.size() / 5);

        ScheduleResult sr = ScheduleResult.builder()
                .projectId(projectId)
                .dataDate(DEFAULT_DATA_DATE)
                .projectStartDate(LocalDate.of(2024, 9, 1))
                .projectFinishDate(LocalDate.of(2026, 8, 31))
                .criticalPathLength(720.0)
                .totalActivities(activities.size())
                .criticalActivities((int) criticalCount)
                .schedulingOption(SchedulingOption.RETAINED_LOGIC)
                .calculatedAt(Instant.parse("2026-04-29T06:00:00Z"))
                .durationSeconds(12.5)
                .status(ScheduleStatus.COMPLETED)
                .build();
        ScheduleResult saved = scheduleResultRepository.save(sr);

        if (baseline != null && baseline.getBaseScheduleResultId() == null) {
            baseline.setBaseScheduleResultId(saved.getId());
            scheduleScenarioRepository.save(baseline);
        }
        return saved;
    }

    private void seedScheduleActivityResults(UUID scheduleResultId, List<Activity> activities, Random rng) {
        LocalDate projectStart = LocalDate.of(2024, 9, 1);
        int created = 0;
        int limit = Math.min(20, activities.size());

        for (int i = 0; i < limit; i++) {
            Activity a = activities.get(i);
            int offsetDays = 10 + rng.nextInt(200);
            int durationDays = 5 + rng.nextInt(40);
            LocalDate earlyStart = projectStart.plusDays(offsetDays);
            LocalDate earlyFinish = earlyStart.plusDays(durationDays);
            LocalDate lateStart = earlyStart.plusDays(rng.nextInt(15));
            LocalDate lateFinish = lateStart.plusDays(durationDays);
            double totalFloat = (double) (rng.nextInt(20) - 3);
            double freeFloat = Math.max(0, totalFloat - rng.nextInt(5));
            boolean isCritical = totalFloat <= 0;
            double remainingDuration = durationDays * (0.3 + rng.nextDouble() * 0.5);

            ScheduleActivityResult sar = ScheduleActivityResult.builder()
                    .scheduleResultId(scheduleResultId)
                    .activityId(a.getId())
                    .earlyStart(earlyStart)
                    .earlyFinish(earlyFinish)
                    .lateStart(lateStart)
                    .lateFinish(lateFinish)
                    .totalFloat(totalFloat)
                    .freeFloat(freeFloat)
                    .isCritical(isCritical)
                    .remainingDuration(Math.round(remainingDuration * 10.0) / 10.0)
                    .build();
            scheduleActivityResultRepository.save(sar);
            created++;
        }
        log.info("[BNK-SCHED] seeded {} schedule activity results", created);
    }

    private void seedCompressionAnalyses(UUID projectId, ScheduleScenario baseline, Random rng) {
        CompressionAnalysis crash = CompressionAnalysis.builder()
                .projectId(projectId)
                .scenarioId(baseline != null ? baseline.getId() : null)
                .analysisType(CompressionType.CRASH)
                .originalDuration(720.0)
                .compressedDuration(702.0)
                .durationSaved(18.0)
                .additionalCost(new BigDecimal("1250000.00"))
                .recommendations("Crash bituminous paving (Sections A + B) with double-shift operations. "
                        + "Additional crew deployed to recover 18-day slip from Wadi crossing soft-strata RFI. "
                        + "Cost impact: OMR 1.25M — funded from contingency reserve.")
                .build();
        compressionAnalysisRepository.save(crash);

        CompressionAnalysis fastTrack = CompressionAnalysis.builder()
                .projectId(projectId)
                .scenarioId(baseline != null ? baseline.getId() : null)
                .analysisType(CompressionType.FAST_TRACK)
                .originalDuration(720.0)
                .compressedDuration(685.0)
                .durationSaved(35.0)
                .additionalCost(new BigDecimal("2500000.00"))
                .recommendations("Fast-track parallel construction of both wadi bridge structures (P1 + P2). "
                        + "Saves 35 calendar days but increases peak resource demand by ~40%. "
                        + "Requires night-shift approval from MoTC. Cost impact: OMR 2.5M.")
                .build();
        compressionAnalysisRepository.save(fastTrack);

        log.info("[BNK-SCHED] seeded 2 compression analyses (CRASH, FAST_TRACK)");
    }

    private void seedPertEstimates(List<Activity> activities, Random rng) {
        int limit = Math.min(10, activities.size());
        int created = 0;

        for (int i = 0; i < limit; i++) {
            Activity a = activities.get(i);
            if (pertEstimateRepository.findByActivityId(a.getId()).isPresent()) continue;

            double mostLikely = 10 + rng.nextInt(50);
            double optimistic = mostLikely * (0.7 + rng.nextDouble() * 0.15);
            double pessimistic = mostLikely * (1.2 + rng.nextDouble() * 0.3);
            double expected = (optimistic + 4 * mostLikely + pessimistic) / 6.0;
            double sd = (pessimistic - optimistic) / 6.0;
            double variance = sd * sd;

            PertEstimate pe = PertEstimate.builder()
                    .activityId(a.getId())
                    .optimisticDuration(Math.round(optimistic * 10.0) / 10.0)
                    .mostLikelyDuration(Math.round(mostLikely * 10.0) / 10.0)
                    .pessimisticDuration(Math.round(pessimistic * 10.0) / 10.0)
                    .expectedDuration(Math.round(expected * 10.0) / 10.0)
                    .standardDeviation(Math.round(sd * 1000.0) / 1000.0)
                    .variance(Math.round(variance * 1000.0) / 1000.0)
                    .build();
            pertEstimateRepository.save(pe);
            created++;
        }
        log.info("[BNK-SCHED] seeded {} PERT estimates", created);
    }

    private void seedScheduleHealthIndex(UUID projectId, UUID scheduleResultId,
                                          List<Activity> activities, Random rng) {
        int total = activities.size();
        int critical = Math.max(1, total / 5);
        int nearCritical = Math.max(1, total / 8);
        double avgFloat = 8.5 + rng.nextDouble() * 6.0;
        double healthScore = 72.0 + rng.nextDouble() * 15.0;

        String floatDistribution = "{\"negative\": " + critical
                + ", \"0-5\": " + nearCritical
                + ", \"6-15\": " + (total / 3)
                + ", \">15\": " + (total - critical - nearCritical - total / 3) + "}";

        RiskLevel riskLevel = healthScore >= 85 ? RiskLevel.LOW
                : (healthScore >= 70 ? RiskLevel.MEDIUM : RiskLevel.HIGH);

        ScheduleHealthIndex shi = ScheduleHealthIndex.builder()
                .scheduleResultId(scheduleResultId)
                .projectId(projectId)
                .totalActivities(total)
                .criticalActivities(critical)
                .nearCriticalActivities(nearCritical)
                .totalFloatAverage(Math.round(avgFloat * 10.0) / 10.0)
                .healthScore(Math.round(healthScore * 10.0) / 10.0)
                .floatDistribution(floatDistribution)
                .riskLevel(riskLevel)
                .build();
        scheduleHealthIndexRepository.save(shi);

        log.info("[BNK-SCHED] seeded schedule health index — score: {}, risk: {}",
                Math.round(healthScore * 10.0) / 10.0, riskLevel);
    }
}
