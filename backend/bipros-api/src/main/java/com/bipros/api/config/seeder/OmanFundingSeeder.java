package com.bipros.api.config.seeder;

import com.bipros.cost.domain.entity.CashFlowForecast;
import com.bipros.cost.domain.entity.FundingSource;
import com.bipros.cost.domain.entity.ProjectFunding;
import com.bipros.cost.domain.repository.CashFlowForecastRepository;
import com.bipros.cost.domain.repository.FundingSourceRepository;
import com.bipros.cost.domain.repository.ProjectFundingRepository;
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
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
@Profile("seed")
@Order(147)
@RequiredArgsConstructor
public class OmanFundingSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";
    private static final long DETERMINISTIC_SEED = 6155L;
    private static final LocalDate DEFAULT_DATA_DATE = LocalDate.of(2026, 4, 29);

    private static final String FUNDING_CODE = "FS-BNK-MOT-OMR";

    private final ProjectRepository projectRepository;
    private final FundingSourceRepository fundingSourceRepository;
    private final ProjectFundingRepository projectFundingRepository;
    private final CashFlowForecastRepository cashFlowForecastRepository;

    @Override
    public void run(String... args) {
        Project project = projectRepository.findByCode(PROJECT_CODE).orElse(null);
        if (project == null) {
            log.warn("[BNK-FUNDING] project '{}' not found — skipping", PROJECT_CODE);
            return;
        }
        UUID projectId = project.getId();

        if (fundingSourceRepository.findByCode(FUNDING_CODE).isPresent()) {
            log.info("[BNK-FUNDING] funding source '{}' already present — skipping", FUNDING_CODE);
            return;
        }

        log.info("[BNK-FUNDING] seeding funding data for project '{}'", PROJECT_CODE);
        Random rng = new Random(DETERMINISTIC_SEED);

        FundingSource source = seedFundingSource();
        seedProjectFunding(projectId, source.getId());
        seedCashFlowForecasts(projectId, rng);

        log.info("[BNK-FUNDING] funding seeding completed");
    }

    private FundingSource seedFundingSource() {
        BigDecimal total = new BigDecimal("80000000.00");
        BigDecimal allocated = new BigDecimal("75000000.00");
        FundingSource fs = new FundingSource();
        fs.setCode(FUNDING_CODE);
        fs.setName("Ministry of Transport OMR");
        fs.setDescription("Ministry of Transport, Sultanate of Oman — earmarked funding for Barka–Nakhal Road "
                + "improvement project (41 km dual carriageway). Currency: OMR.");
        fs.setTotalAmount(total);
        fs.setAllocatedAmount(allocated);
        fs.setRemainingAmount(total.subtract(allocated));
        return fundingSourceRepository.save(fs);
    }

    private void seedProjectFunding(UUID projectId, UUID fundingSourceId) {
        ProjectFunding pf = new ProjectFunding();
        pf.setProjectId(projectId);
        pf.setFundingSourceId(fundingSourceId);
        pf.setAllocatedAmount(new BigDecimal("75000000.00"));
        projectFundingRepository.save(pf);
    }

    private void seedCashFlowForecasts(UUID projectId, Random rng) {
        BigDecimal totalPlanned = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        BigDecimal totalForecast = BigDecimal.ZERO;

        for (int i = 0; i < 12; i++) {
            int year = 2025 + (i / 12);
            int month = 4 + (i % 12);
            if (month > 12) {
                month -= 12;
                year++;
            }
            String period = String.format("%d-%02d", year, month);

            BigDecimal planned = computePlannedAmount(i);
            BigDecimal actual;
            BigDecimal forecast;
            if (i < 9) {
                double variation = 0.88 + rng.nextDouble() * 0.24;
                actual = planned.multiply(BigDecimal.valueOf(variation)).setScale(2, RoundingMode.HALF_UP);
                forecast = actual;
            } else {
                actual = null;
                double variation = 0.90 + rng.nextDouble() * 0.20;
                forecast = planned.multiply(BigDecimal.valueOf(variation)).setScale(2, RoundingMode.HALF_UP);
            }

            totalPlanned = totalPlanned.add(planned);
            if (actual != null) totalActual = totalActual.add(actual);
            totalForecast = totalForecast.add(forecast);

            CashFlowForecast cff = new CashFlowForecast();
            cff.setProjectId(projectId);
            cff.setPeriod(period);
            cff.setPlannedAmount(planned.setScale(2, RoundingMode.HALF_UP));
            cff.setActualAmount(actual != null ? actual.setScale(2, RoundingMode.HALF_UP) : null);
            cff.setForecastAmount(forecast.setScale(2, RoundingMode.HALF_UP));
            cff.setCumulativePlanned(totalPlanned.setScale(2, RoundingMode.HALF_UP));
            cff.setCumulativeActual(actual != null ? totalActual.setScale(2, RoundingMode.HALF_UP) : null);
            cff.setCumulativeForecast(totalForecast.setScale(2, RoundingMode.HALF_UP));
            cashFlowForecastRepository.save(cff);
        }
        log.info("[BNK-FUNDING] seeded 12 cash flow forecast rows");
    }

    private BigDecimal computePlannedAmount(int monthIndex) {
        BigDecimal totalBudget = new BigDecimal("75000000.00");
        double[] monthlyPcts = {
                0.04, 0.06, 0.08, 0.10, 0.10, 0.10,
                0.10, 0.10, 0.09, 0.08, 0.08, 0.07
        };
        return totalBudget.multiply(BigDecimal.valueOf(monthlyPcts[monthIndex]))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
