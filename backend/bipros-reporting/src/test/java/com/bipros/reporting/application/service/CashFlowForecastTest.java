package com.bipros.reporting.application.service;

import com.bipros.reporting.application.dto.CashFlowEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReportDataService#buildCashFlowSeries}.
 *
 * Assertions:
 * <ol>
 *   <li>Σ planned == BAC (planned series is budget-anchored)</li>
 *   <li>Cumulative forecast of the last entry == EAC</li>
 *   <li>Cumulative forecast never falls below cumulative actual at any month</li>
 * </ol>
 */
class CashFlowForecastTest {

    /**
     * Typical scenario: 2 past months with actual data, 2 future months planned only.
     * BAC differs from the raw planned total so we can verify scaling.
     * EAC > cumulative actual so ETC is positive and distributed across future months.
     */
    @Test
    void plannedSumsToBac_forecastEndAtEac_neverBelowActual() {
        Set<String> months = new TreeSet<>(List.of("2026-01", "2026-02", "2026-03", "2026-04"));

        // Raw planned totals: 1M + 1M + 2M + 2M = 6M
        Map<String, BigDecimal> planned = new TreeMap<>(Map.of(
                "2026-01", new BigDecimal("1000000"),
                "2026-02", new BigDecimal("1000000"),
                "2026-03", new BigDecimal("2000000"),
                "2026-04", new BigDecimal("2000000")));

        // Actual only in first 2 months: 500k + 700k = 1.2M total
        Map<String, BigDecimal> actual = new TreeMap<>(Map.of(
                "2026-01", new BigDecimal("500000"),
                "2026-02", new BigDecimal("700000")));

        // BAC = 100M (very different from the 6M resource-loaded planned)
        BigDecimal bac = new BigDecimal("100000000");
        // EAC = 81M (the budget-anchored remaining forecast after actual)
        BigDecimal eac = new BigDecimal("81000000");

        List<CashFlowEntry> result = ReportDataService.buildCashFlowSeries(months, planned, actual, bac, eac);

        assertThat(result).hasSize(4);

        // 1. Σ planned == BAC (within 1 unit for rounding)
        BigDecimal sumPlanned = result.stream()
                .map(CashFlowEntry::planned)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumPlanned.subtract(bac).abs().compareTo(new BigDecimal("1")))
                .isLessThanOrEqualTo(0);

        // 2. Last cumulative forecast == EAC (within 1 unit for rounding)
        CashFlowEntry last = result.get(result.size() - 1);
        assertThat(last.cumulativeForecast().subtract(eac).abs().compareTo(new BigDecimal("1")))
                .isLessThanOrEqualTo(0);

        // 3. Cumulative forecast never below cumulative actual at any point
        for (CashFlowEntry e : result) {
            assertThat(e.cumulativeForecast())
                    .as("cumForecast (%s) < cumActual (%s) at %s",
                            e.cumulativeForecast(), e.cumulativeActual(), e.period())
                    .isGreaterThanOrEqualTo(e.cumulativeActual());
        }
    }

    /**
     * Edge case: no planned data, all months are past (actual > 0 in every month).
     * With bac > 0 and plannedTotal == 0, scale defaults to 1 (no crash).
     * Since every month has actual data, forecastMonth == actual for each month,
     * so the per-entry monthly forecast should equal the monthly actual and
     * the final cumulative forecast should equal the sum of all actuals.
     */
    @Test
    void noPlannedData_allPastMonths_forecastTracksActual() {
        Set<String> months = new TreeSet<>(List.of("2026-01", "2026-02"));
        Map<String, BigDecimal> planned = new TreeMap<>();
        Map<String, BigDecimal> actual = new TreeMap<>(Map.of(
                "2026-01", new BigDecimal("400000"),
                "2026-02", new BigDecimal("300000")));

        BigDecimal bac = new BigDecimal("100000000");
        BigDecimal eac = new BigDecimal("90000000");

        List<CashFlowEntry> result = ReportDataService.buildCashFlowSeries(months, planned, actual, bac, eac);

        // Past months keep forecast == actual; ETC remainder is placed in a synthetic trailing period.
        assertThat(result).hasSize(3);
        // Monthly forecast == monthly actual for each past month.
        assertThat(result.get(0).forecast()).isEqualByComparingTo("400000");
        assertThat(result.get(1).forecast()).isEqualByComparingTo("300000");
        // Trailing period carries the ETC remainder so cumulative forecast reaches EAC.
        assertThat(result.get(2).period()).isEqualTo("2026-03");
        assertThat(result.get(2).cumulativeForecast().subtract(eac).abs())
                .isLessThanOrEqualTo(new BigDecimal("1"));
        // Invariant: cumulative forecast never below cumulative actual.
        for (CashFlowEntry e : result) {
            assertThat(e.cumulativeForecast()).isGreaterThanOrEqualTo(e.cumulativeActual());
        }
    }

    /**
     * Edge case: EAC < Σactual (project is already overspent).
     * ETC = max(0, eac − totalActual) clamps to 0, so future months get zero forecast.
     * Final cumulative forecast must equal totalActual — it must not drop below it
     * and must not inflate it further.
     */
    @Test
    void overspent_etcClampsToZero_futureMonthsForecastZero() {
        Set<String> months = new TreeSet<>(List.of("2026-01", "2026-02", "2026-03", "2026-04"));
        Map<String, BigDecimal> planned = new TreeMap<>(Map.of(
                "2026-01", new BigDecimal("1000000"),
                "2026-02", new BigDecimal("1000000"),
                "2026-03", new BigDecimal("2000000"),
                "2026-04", new BigDecimal("2000000")));
        // Two past months totalling 1.2M — already exceeds EAC of 500k.
        Map<String, BigDecimal> actual = new TreeMap<>(Map.of(
                "2026-01", new BigDecimal("700000"),
                "2026-02", new BigDecimal("500000")));

        BigDecimal bac = new BigDecimal("100000000");
        BigDecimal eac = new BigDecimal("500000");   // EAC < Σactual
        BigDecimal totalActual = new BigDecimal("1200000");

        List<CashFlowEntry> result = ReportDataService.buildCashFlowSeries(months, planned, actual, bac, eac);

        assertThat(result).hasSize(4);
        // Future months (2026-03, 2026-04) should have zero monthly forecast.
        assertThat(result.get(2).forecast()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.get(3).forecast()).isEqualByComparingTo(BigDecimal.ZERO);
        // Final cumulative forecast == totalActual (not less, not artificially inflated).
        assertThat(result.get(3).cumulativeForecast()).isEqualByComparingTo(totalActual);
        // Invariant: cumulative forecast never below cumulative actual.
        for (CashFlowEntry e : result) {
            assertThat(e.cumulativeForecast()).isGreaterThanOrEqualTo(e.cumulativeActual());
        }
    }

    /**
     * Edge case: BAC = 0 → scale defaults to 1 (no crash), planned passes through unscaled.
     */
    @Test
    void zeroBac_noScalingApplied_noException() {
        Set<String> months = new TreeSet<>(List.of("2026-01", "2026-02"));
        Map<String, BigDecimal> planned = new TreeMap<>(Map.of(
                "2026-01", new BigDecimal("500000"),
                "2026-02", new BigDecimal("500000")));
        Map<String, BigDecimal> actual = new TreeMap<>();

        List<CashFlowEntry> result = ReportDataService.buildCashFlowSeries(
                months, planned, actual, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result).hasSize(2);
        // With BAC=0 scale=1, planned passes through; with EAC=0 etc=0 → forecasts=0
        assertThat(result.get(0).planned()).isEqualByComparingTo("500000");
        assertThat(result.get(1).cumulativeForecast()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
