package com.bipros.analytics.etl.batch;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.store.ClickHouseTemplate;
import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.evm.domain.repository.EvmCalculationRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interpolates project-level EVM calculations to daily grain for ClickHouse.
 *
 * <p>Closed periods (between two consecutive data dates): linear interpolation for PV/EV/AC.
 * Open period (latest data date to today): carry-forward (repeat latest values).
 *
 * <p>Idempotent: each re-run overwrites via ReplacingMergeTree(_version).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvmDailyInterpolator {

    private final EvmCalculationRepository evmCalculationRepository;
    private final ProjectRepository projectRepository;
    private final ClickHouseTemplate clickHouse;
    private final AnalyticsEtlService etl;

    public void interpolateProject(UUID projectId) {
        List<EvmCalculation> calcs = evmCalculationRepository.findByProjectIdOrderByDataDateDesc(projectId);
        if (calcs.isEmpty()) {
            return;
        }

        // Order ascending by date for interpolation
        List<EvmCalculation> ordered = calcs.reversed();

        for (int i = 0; i < ordered.size(); i++) {
            EvmCalculation current = ordered.get(i);
            EvmCalculation previous = i > 0 ? ordered.get(i - 1) : null;

            LocalDate fromDate = previous != null ? previous.getDataDate() : current.getDataDate();
            LocalDate toDate = current.getDataDate();
            boolean isOpenPeriod = (i == ordered.size() - 1);

            if (isOpenPeriod) {
                // Carry-forward from latest data date to today (or 30 days ahead)
                LocalDate carryUntil = LocalDate.now().plusDays(1);
                generateCarryForward(projectId, current, toDate, carryUntil);
            } else {
                // Linear interpolation between previous and current
                generateLinearInterpolation(projectId, previous, current, fromDate, toDate);
            }
        }

        log.debug("Interpolated EVM daily for project={} over {} periods", projectId, ordered.size());
    }

    private void generateLinearInterpolation(UUID projectId, EvmCalculation start, EvmCalculation end,
                                             LocalDate fromDate, LocalDate toDate) {
        long days = ChronoUnit.DAYS.between(fromDate, toDate);
        if (days <= 0) {
            insertDaily(projectId, null, null, toDate, end, "linear");
            return;
        }

        BigDecimal pvStart = start.getPlannedValue() != null ? start.getPlannedValue() : BigDecimal.ZERO;
        BigDecimal evStart = start.getEarnedValue() != null ? start.getEarnedValue() : BigDecimal.ZERO;
        BigDecimal acStart = start.getActualCost() != null ? start.getActualCost() : BigDecimal.ZERO;

        BigDecimal pvEnd = end.getPlannedValue() != null ? end.getPlannedValue() : BigDecimal.ZERO;
        BigDecimal evEnd = end.getEarnedValue() != null ? end.getEarnedValue() : BigDecimal.ZERO;
        BigDecimal acEnd = end.getActualCost() != null ? end.getActualCost() : BigDecimal.ZERO;

        BigDecimal bac = end.getBudgetAtCompletion() != null ? end.getBudgetAtCompletion() : BigDecimal.ZERO;

        for (int d = 1; d <= days; d++) {
            LocalDate date = fromDate.plusDays(d);
            BigDecimal ratio = BigDecimal.valueOf(d).divide(BigDecimal.valueOf(days), 10, RoundingMode.HALF_UP);

            BigDecimal pv = interpolate(pvStart, pvEnd, ratio);
            BigDecimal ev = interpolate(evStart, evEnd, ratio);
            BigDecimal ac = interpolate(acStart, acEnd, ratio);

            BigDecimal cv = ev.subtract(ac);
            BigDecimal sv = ev.subtract(pv);
            Double cpi = ac.compareTo(BigDecimal.ZERO) != 0 ? ev.divide(ac, 10, RoundingMode.HALF_UP).doubleValue() : null;
            Double spi = pv.compareTo(BigDecimal.ZERO) != 0 ? ev.divide(pv, 10, RoundingMode.HALF_UP).doubleValue() : null;

            insertRaw(projectId, null, null, date, bac, pv, ev, ac, cv, sv, cpi, spi,
                    null, null, null, null, "period-grain", "linear");
        }
    }

    private void generateCarryForward(UUID projectId, EvmCalculation latest,
                                      LocalDate fromDate, LocalDate untilDate) {
        BigDecimal pv = latest.getPlannedValue() != null ? latest.getPlannedValue() : BigDecimal.ZERO;
        BigDecimal ev = latest.getEarnedValue() != null ? latest.getEarnedValue() : BigDecimal.ZERO;
        BigDecimal ac = latest.getActualCost() != null ? latest.getActualCost() : BigDecimal.ZERO;
        BigDecimal bac = latest.getBudgetAtCompletion() != null ? latest.getBudgetAtCompletion() : BigDecimal.ZERO;
        BigDecimal cv = latest.getCostVariance() != null ? latest.getCostVariance() : ev.subtract(ac);
        BigDecimal sv = latest.getScheduleVariance() != null ? latest.getScheduleVariance() : ev.subtract(pv);
        Double cpi = latest.getCostPerformanceIndex();
        Double spi = latest.getSchedulePerformanceIndex();
        Double tcpi = latest.getToCompletePerformanceIndex();
        BigDecimal eac = latest.getEstimateAtCompletion();
        BigDecimal etc = latest.getEstimateToComplete();
        BigDecimal vac = latest.getVarianceAtCompletion();

        LocalDate date = fromDate;
        while (!date.isAfter(untilDate)) {
            insertRaw(projectId, null, null, date, bac, pv, ev, ac, cv, sv, cpi, spi,
                    tcpi, eac, etc, vac, "period-grain", "carry-forward");
            date = date.plusDays(1);
        }
    }

    private void insertDaily(UUID projectId, UUID wbsId, UUID activityId, LocalDate date,
                             EvmCalculation calc, String interpolation) {
        etl.insertEvmDaily(projectId, wbsId, activityId, date,
                calc.getBudgetAtCompletion(), calc.getPlannedValue(), calc.getEarnedValue(),
                calc.getActualCost(), calc.getCostVariance(), calc.getScheduleVariance(),
                calc.getCostPerformanceIndex(), calc.getSchedulePerformanceIndex(),
                calc.getToCompletePerformanceIndex(), calc.getEstimateAtCompletion(),
                calc.getEstimateToComplete(), calc.getVarianceAtCompletion(),
                "period-grain", interpolation);
    }

    private void insertRaw(UUID projectId, UUID wbsId, UUID activityId, LocalDate date,
                           BigDecimal bac, BigDecimal pv, BigDecimal ev, BigDecimal ac,
                           BigDecimal cv, BigDecimal sv, Double cpi, Double spi, Double tcpi,
                           BigDecimal eac, BigDecimal etc, BigDecimal vac,
                           String periodSource, String interpolation) {
        etl.insertEvmDaily(projectId, wbsId, activityId, date,
                bac, pv, ev, ac, cv, sv, cpi, spi, tcpi, eac, etc, vac,
                periodSource, interpolation);
    }

    private BigDecimal interpolate(BigDecimal start, BigDecimal end, BigDecimal ratio) {
        if (start == null) start = BigDecimal.ZERO;
        if (end == null) end = BigDecimal.ZERO;
        BigDecimal delta = end.subtract(start);
        return start.add(delta.multiply(ratio));
    }
}
