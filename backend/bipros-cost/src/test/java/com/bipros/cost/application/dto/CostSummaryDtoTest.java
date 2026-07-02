package com.bipros.cost.application.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the EVM factory. Numbers are hand-computed so a future refactor that
 * breaks the math fails here rather than silently diverging from docs/calculations/costs-tab.md.
 */
class CostSummaryDtoTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @Test
    void evm_metrics_match_hand_computed_values() {
        // BAC = 100,000,000; AC = 4,820,000; BOQ budgeted total = 100,000,000;
        // BOQ earned = 24,000,000  -> cost%complete = 0.24 -> EV = 24,000,000.
        // planned%complete = 0.30 -> PV = 30,000,000.
        CostSummaryDto dto = CostSummaryDto.ofEvm(
                bd("100000000"),   // bac
                bd("3380000"),     // plannedCost (bottom-up)
                bd("4820000"),     // actualCost (AC)
                bd("100000000"),   // boqBudgetedTotal
                bd("24000000"),    // boqEarnedValue
                bd("0.30"),        // plannedPercentComplete
                5, null, null, null, null, null);

        assertThat(dto.bac()).isEqualByComparingTo("100000000");
        assertThat(dto.costPercentComplete()).isEqualByComparingTo("0.24");
        assertThat(dto.earnedValue()).isEqualByComparingTo("24000000");
        assertThat(dto.plannedValue()).isEqualByComparingTo("30000000");
        assertThat(dto.totalActual()).isEqualByComparingTo("4820000");
        assertThat(dto.costVariance()).isEqualByComparingTo("19180000");          // EV - AC
        assertThat(dto.costPerformanceIndex()).isEqualByComparingTo("4.9793");    // 24,000,000 / 4,820,000
        assertThat(dto.scheduleVariance()).isEqualByComparingTo("-6000000");      // EV - PV
        assertThat(dto.schedulePerformanceIndex()).isEqualByComparingTo("0.8000");// EV / PV
        assertThat(dto.estimateAtCompletion()).isEqualByComparingTo("20083144.22"); // BAC / CPI (100,000,000 ÷ 4.9793)
        assertThat(dto.estimateToComplete()).isEqualByComparingTo("15263144.22");   // EAC - AC
        assertThat(dto.varianceAtCompletion()).isEqualByComparingTo("79916855.78"); // BAC - EAC
        assertThat(dto.toCompletePerformanceIndex()).isEqualByComparingTo("0.7985"); // (BAC-EV)/(BAC-AC)
    }

    @Test
    void zero_actual_cost_yields_null_cpi_and_eac_falls_back_to_bac() {
        CostSummaryDto dto = CostSummaryDto.ofEvm(
                bd("100000000"), bd("3380000"), bd("0"),
                bd("100000000"), bd("0"), bd("0"),
                0, null, null, null, null, null);

        assertThat(dto.costPerformanceIndex()).isNull();
        assertThat(dto.earnedValue()).isEqualByComparingTo("0");
        assertThat(dto.estimateAtCompletion()).isEqualByComparingTo("100000000"); // falls back to BAC
        assertThat(dto.schedulePerformanceIndex()).isNull();                       // PV = 0
    }

    @Test
    void ofEvm_clamps_cost_pct_and_ev_when_earned_slightly_exceeds_budgeted() {
        // Simulates rounding-precision drift: earned (100.01) > budgeted (100.00) by 0.01.
        // costPct would be ~1.000100 without the clamp; EV would be ~1000.10 against BAC=1000.
        // After the clamp: costPercentComplete == 1 and earnedValue == BAC == 1000.
        CostSummaryDto dto = CostSummaryDto.ofEvm(
                bd("1000"),    // bac
                bd("0"),       // plannedCost
                bd("0"),       // actualCost (AC)
                bd("100.00"),  // boqBudgetedTotal
                bd("100.01"),  // boqEarnedValue — slightly over (rounding drift)
                bd("0"),       // plannedPercentComplete
                0, null, null, null, null, null);

        assertThat(dto.costPercentComplete()).isEqualByComparingTo("1");
        assertThat(dto.earnedValue()).isEqualByComparingTo("1000");
    }

    @Test
    void zero_boq_budgeted_total_yields_zero_progress_and_zero_ev() {
        CostSummaryDto dto = CostSummaryDto.ofEvm(
                bd("100000000"), bd("3380000"), bd("4820000"),
                bd("0"), bd("0"), bd("0.30"),
                0, null, null, null, null, null);

        assertThat(dto.costPercentComplete()).isEqualByComparingTo("0");
        assertThat(dto.earnedValue()).isEqualByComparingTo("0");
        assertThat(dto.costVariance()).isEqualByComparingTo("-4820000");          // 0 - AC
    }
}
