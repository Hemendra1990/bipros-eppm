package com.bipros.api.config.seeder;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.cost.domain.entity.FinancialPeriod;
import com.bipros.cost.domain.entity.StorePeriodPerformance;
import com.bipros.cost.domain.repository.FinancialPeriodRepository;
import com.bipros.cost.domain.repository.StorePeriodPerformanceRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
@Profile("seed")
@Order(148)
@RequiredArgsConstructor
public class OmanFinancialPeriodSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";
    private static final long DETERMINISTIC_SEED = 6155L;
    private static final LocalDate DEFAULT_DATA_DATE = LocalDate.of(2026, 4, 29);

    private final ProjectRepository projectRepository;
    private final FinancialPeriodRepository financialPeriodRepository;
    private final StorePeriodPerformanceRepository storePeriodPerformanceRepository;
    private final ActivityRepository activityRepository;

    @Override
    public void run(String... args) {
        Project project = projectRepository.findByCode(PROJECT_CODE).orElse(null);
        if (project == null) {
            log.warn("[BNK-FINPERIOD] project '{}' not found — skipping", PROJECT_CODE);
            return;
        }
        UUID projectId = project.getId();

        if (!financialPeriodRepository.findAllByOrderBySortOrder().isEmpty()) {
            log.info("[BNK-FINPERIOD] financial periods already present — skipping");
            return;
        }

        log.info("[BNK-FINPERIOD] seeding financial periods for project '{}'", PROJECT_CODE);
        Random rng = new Random(DETERMINISTIC_SEED);

        List<FinancialPeriod> periods = seedFinancialPeriods();
        seedStorePeriodPerformances(projectId, periods, rng);

        log.info("[BNK-FINPERIOD] financial period seeding completed");
    }

    private List<FinancialPeriod> seedFinancialPeriods() {
        List<FinancialPeriod> periods = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            int year = 2025 + (i / 12);
            int month = 4 + (i % 12);
            if (month > 12) {
                month -= 12;
                year++;
            }
            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            String name = start.getMonth().name().substring(0, 1)
                    + start.getMonth().name().substring(1).toLowerCase()
                    + " " + year;

            boolean isClosed = i < 9;

            FinancialPeriod fp = new FinancialPeriod();
            fp.setName(name);
            fp.setStartDate(start);
            fp.setEndDate(end);
            fp.setPeriodType("MONTHLY");
            fp.setIsClosed(isClosed);
            fp.setSortOrder(i + 1);
            periods.add(financialPeriodRepository.save(fp));
        }
        log.info("[BNK-FINPERIOD] seeded {} financial periods (Apr 2025 – Mar 2026)", periods.size());
        return periods;
    }

    private void seedStorePeriodPerformances(UUID projectId, List<FinancialPeriod> periods, Random rng) {
        List<Activity> activities = activityRepository.findByProjectId(projectId);
        if (activities.isEmpty()) {
            log.warn("[BNK-FINPERIOD] no activities found — skipping store period performance");
            return;
        }

        int targetActivities = Math.min(20, activities.size());
        int created = 0;

        for (int pi = 0; pi < periods.size(); pi++) {
            FinancialPeriod period = periods.get(pi);
            if (!Boolean.TRUE.equals(period.getIsClosed())) continue;

            for (int ai = 0; ai < targetActivities && ai < activities.size(); ai++) {
                Activity activity = activities.get(ai);
                if (storePeriodPerformanceRepository
                        .findByProjectIdAndFinancialPeriodIdAndActivityIdIsNull(projectId, period.getId())
                        .isPresent() && ai == 0) {
                    continue;
                }

                double baseLaborCost = 5000 + rng.nextInt(15000);
                double baseMaterialCost = 8000 + rng.nextInt(20000);
                double baseExpenseCost = 1000 + rng.nextInt(5000);
                double baseNonlaborCost = 2000 + rng.nextInt(8000);

                double scale = 0.7 + (pi / 12.0) * 0.6;

                StorePeriodPerformance spp = new StorePeriodPerformance();
                spp.setProjectId(projectId);
                spp.setFinancialPeriodId(period.getId());
                spp.setActivityId(activity.getId());
                spp.setActualLaborCost(BigDecimal.valueOf(baseLaborCost * scale).setScale(2, RoundingMode.HALF_UP));
                spp.setActualNonlaborCost(BigDecimal.valueOf(baseNonlaborCost * scale).setScale(2, RoundingMode.HALF_UP));
                spp.setActualMaterialCost(BigDecimal.valueOf(baseMaterialCost * scale).setScale(2, RoundingMode.HALF_UP));
                spp.setActualExpenseCost(BigDecimal.valueOf(baseExpenseCost * scale).setScale(2, RoundingMode.HALF_UP));
                spp.setActualLaborUnits(round2(40 + rng.nextInt(80) * scale));
                spp.setActualNonlaborUnits(round2(10 + rng.nextInt(30) * scale));
                spp.setActualMaterialUnits(round2(20 + rng.nextInt(60) * scale));

                BigDecimal earnedValue = BigDecimal.valueOf((baseLaborCost + baseMaterialCost) * scale * 0.95)
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal plannedValue = BigDecimal.valueOf((baseLaborCost + baseMaterialCost) * scale * 1.05)
                        .setScale(2, RoundingMode.HALF_UP);
                spp.setEarnedValueCost(earnedValue);
                spp.setPlannedValueCost(plannedValue);

                storePeriodPerformanceRepository.save(spp);
                created++;
            }
        }
        log.info("[BNK-FINPERIOD] seeded {} store period performance rows", created);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
