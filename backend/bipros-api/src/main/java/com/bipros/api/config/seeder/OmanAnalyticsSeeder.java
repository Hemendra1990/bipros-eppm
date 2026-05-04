package com.bipros.api.config.seeder;

import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.reporting.domain.model.AnalyticsQuery;
import com.bipros.reporting.domain.model.Prediction;
import com.bipros.reporting.domain.repository.AnalyticsQueryRepository;
import com.bipros.reporting.domain.repository.PredictionRepository;
import com.bipros.risk.domain.model.MonteCarloCashflowBucket;
import com.bipros.risk.domain.model.MonteCarloSimulation;
import com.bipros.risk.domain.repository.MonteCarloCashflowBucketRepository;
import com.bipros.risk.domain.repository.MonteCarloSimulationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
@Profile("seed")
@Order(158)
@RequiredArgsConstructor
public class OmanAnalyticsSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";
    private static final long DETERMINISTIC_SEED = 6155L;
    private static final LocalDate DEFAULT_DATA_DATE = LocalDate.of(2026, 4, 29);

    private final ProjectRepository projectRepository;
    private final AnalyticsQueryRepository analyticsQueryRepository;
    private final PredictionRepository predictionRepository;
    private final MonteCarloSimulationRepository monteCarloSimulationRepository;
    private final MonteCarloCashflowBucketRepository monteCarloCashflowBucketRepository;

    private static final String[][] QUERY_DEFS = {
        {"PROJECT_STATUS", "What is the overall status of project 6155?", "Project 6155 (Barka\u2013Nakhal) is 42% complete with SPI=0.96, CPI=0.98. Schedule status: ON_TRACK. 3 open risks, 2 pending RFIs."},
        {"COST_QUERY",     "Show cost breakdown for project 6155",       "Total budget: OMR 12.5M. Spent: OMR 5.2M. Committed: OMR 2.1M. Forecast at completion: OMR 12.8M (2.4% overrun)."},
        {"SCHEDULE_QUERY", "What is the critical path for project 6155?", "Critical path runs through BNK-S2 (Wadi Al Hattat bridge section). Total float: 12 days. Key milestone: Substantial completion by 2026-08-31."},
        {"RISK_QUERY",     "List top risks for project 6155",            "Top 3 risks: RISK-0001 (land acquisition, score=20/25), RISK-0004 (monsoon impact, score=16/25), RISK-0008 (contractor financial, score=15/25)."},
        {"RESOURCE_QUERY", "Show resource utilisation for project 6155", "Equipment utilisation: 82%. Labour attendance: 88%. Critical shortage: Vibratory Roller (only 1 of 3 available). Overtime hours this week: 120."},
    };

    @Override
    public void run(String... args) {
        Optional<Project> projectOpt = projectRepository.findByCode(PROJECT_CODE);
        if (projectOpt.isEmpty()) {
            log.warn("[BNK-ANALYTICS] project '{}' not found — skipping", PROJECT_CODE);
            return;
        }
        Project project = projectOpt.get();
        UUID projectId = project.getId();

        if (!analyticsQueryRepository.findByUserIdOrderByCreatedAtDesc("system", org.springframework.data.domain.PageRequest.of(0, 1)).isEmpty()) {
            log.info("[BNK-ANALYTICS] analytics queries already present — skipping");
            return;
        }

        Random rng = new Random(DETERMINISTIC_SEED);

        int queryCount = seedAnalyticsQueries();
        int predictionCount = seedPredictions(projectId);
        int bucketCount = seedMonteCarloCashflowBuckets(projectId, rng);

        log.info("[BNK-ANALYTICS] Seeded {} analytics queries, {} predictions, {} Monte Carlo cashflow buckets",
            queryCount, predictionCount, bucketCount);
    }

    private int seedAnalyticsQueries() {
        Instant now = Instant.now();
        int created = 0;
        for (String[] def : QUERY_DEFS) {
            AnalyticsQuery q = new AnalyticsQuery();
            q.setUserId("system");
            q.setQueryText(def[1]);
            q.setQueryType(AnalyticsQuery.QueryType.valueOf(def[0]));
            q.setResponseText(def[2]);
            q.setResponseData("{\"status\":\"ok\",\"source\":\"seed\"}");
            q.setResponseTimeMs(45L + created * 12L);
            analyticsQueryRepository.save(q);
            created++;
        }
        return created;
    }

    private int seedPredictions(UUID projectId) {
        Instant now = Instant.now();
        int created = 0;

        Prediction costOverrun = new Prediction();
        costOverrun.setProjectId(projectId);
        costOverrun.setPredictionType(Prediction.PredictionType.COST_OVERRUN);
        costOverrun.setPredictedValue(8.0);
        costOverrun.setConfidenceLevel(0.75);
        costOverrun.setBaselineValue(0.0);
        costOverrun.setVariance(8.0);
        costOverrun.setFactors("[\"material_price_escalation\",\"weather_delays\",\"scope_changes\"]");
        costOverrun.setModelVersion("bnk-forecast-v1.2");
        costOverrun.setCalculatedAt(now.minusSeconds(86400));
        predictionRepository.save(costOverrun);
        created++;

        Prediction scheduleSlip = new Prediction();
        scheduleSlip.setProjectId(projectId);
        scheduleSlip.setPredictionType(Prediction.PredictionType.SCHEDULE_SLIP);
        scheduleSlip.setPredictedValue(21.0);
        scheduleSlip.setConfidenceLevel(0.68);
        scheduleSlip.setBaselineValue(0.0);
        scheduleSlip.setVariance(21.0);
        scheduleSlip.setFactors("[\"monsoon_delays\",\"utility_shifting\",\"material_procurement\"]");
        scheduleSlip.setModelVersion("bnk-forecast-v1.2");
        scheduleSlip.setCalculatedAt(now.minusSeconds(86400));
        predictionRepository.save(scheduleSlip);
        created++;

        Prediction completionDate = new Prediction();
        completionDate.setProjectId(projectId);
        completionDate.setPredictionType(Prediction.PredictionType.COMPLETION_DATE);
        completionDate.setPredictedValue(2026.58);
        completionDate.setConfidenceLevel(0.82);
        completionDate.setBaselineValue(2026.50);
        completionDate.setVariance(0.08);
        completionDate.setFactors("[\"current_progress_rate\",\"remaining_risks\",\"resource_availability\"]");
        completionDate.setModelVersion("bnk-forecast-v1.2");
        completionDate.setCalculatedAt(now.minusSeconds(86400));
        predictionRepository.save(completionDate);
        created++;

        return created;
    }

    private int seedMonteCarloCashflowBuckets(UUID projectId, Random rng) {
        Optional<MonteCarloSimulation> existingSim = monteCarloSimulationRepository.findLatestByProjectId(projectId);
        UUID simulationId;
        if (existingSim.isPresent()) {
            simulationId = existingSim.get().getId();
        } else {
            MonteCarloSimulation sim = new MonteCarloSimulation();
            sim.setProjectId(projectId);
            sim.setSimulationName("BNK-6155 Cashflow Monte Carlo");
            sim.setIterations(10000);
            sim.setBaselineDuration(365.0);
            sim.setBaselineCost(BigDecimal.valueOf(12_500_000));
            sim.setDataDate(DEFAULT_DATA_DATE);
            sim.setIterationsCompleted(10000);
            sim.setStatus(MonteCarloSimulation.MonteCarloStatus.COMPLETED);
            sim.setCompletedAt(Instant.now().minusSeconds(3600));
            MonteCarloSimulation saved = monteCarloSimulationRepository.save(sim);
            simulationId = saved.getId();
        }

        int months = 12;
        double monthlyBaseline = 12_500_000.0 / months;
        double cumulativeBaseline = 0;
        double cumulativeP10 = 0;
        double cumulativeP50 = 0;
        double cumulativeP80 = 0;
        double cumulativeP90 = 0;
        int created = 0;

        for (int m = 0; m < months; m++) {
            LocalDate periodEnd = LocalDate.of(2026, 1, 1).plusMonths(m).withDayOfMonth(
                LocalDate.of(2026, 1, 1).plusMonths(m).lengthOfMonth());

            double spendFactor = 0.6 + rng.nextDouble() * 0.8;
            double monthBaseline = monthlyBaseline * spendFactor;
            cumulativeBaseline += monthBaseline;
            cumulativeP10 += monthBaseline * (0.85 + rng.nextDouble() * 0.10);
            cumulativeP50 += monthBaseline * (0.95 + rng.nextDouble() * 0.10);
            cumulativeP80 += monthBaseline * (1.05 + rng.nextDouble() * 0.10);
            cumulativeP90 += monthBaseline * (1.10 + rng.nextDouble() * 0.15);

            MonteCarloCashflowBucket bucket = MonteCarloCashflowBucket.builder()
                .simulationId(simulationId)
                .periodEndDate(periodEnd)
                .baselineCumulative(BigDecimal.valueOf(cumulativeBaseline).setScale(2, RoundingMode.HALF_UP))
                .p10Cumulative(BigDecimal.valueOf(cumulativeP10).setScale(2, RoundingMode.HALF_UP))
                .p50Cumulative(BigDecimal.valueOf(cumulativeP50).setScale(2, RoundingMode.HALF_UP))
                .p80Cumulative(BigDecimal.valueOf(cumulativeP80).setScale(2, RoundingMode.HALF_UP))
                .p90Cumulative(BigDecimal.valueOf(cumulativeP90).setScale(2, RoundingMode.HALF_UP))
                .build();
            monteCarloCashflowBucketRepository.save(bucket);
            created++;
        }
        return created;
    }
}
