package com.bipros.api.dprreport;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DprReportMetricsCalculatorTest {

    @Test void efficiency_below_90_is_critical() {
        // Client workbook bands (Web sheet, Capacity Utilization): <90 red.
        assertThat(DprReportMetricsCalculator.efficiencySeverity(75.0)).isEqualTo("critical");
        assertThat(DprReportMetricsCalculator.efficiencySeverity(89.9)).isEqualTo("critical");
    }
    @Test void efficiency_90_to_99_is_warning() {
        assertThat(DprReportMetricsCalculator.efficiencySeverity(92.0)).isEqualTo("warning");
    }
    @Test void efficiency_100_plus_is_info() {
        assertThat(DprReportMetricsCalculator.efficiencySeverity(140.0)).isEqualTo("info");
    }
    @Test void efficiency_null_is_info() {
        assertThat(DprReportMetricsCalculator.efficiencySeverity(null)).isEqualTo("info");
    }
    @Test void cost_overrun_flags_when_positive_implication() {
        // sign convention: positive costImplication = overrun
        assertThat(DprReportMetricsCalculator.isCostOverrun(1200.0)).isTrue();
        assertThat(DprReportMetricsCalculator.isCostOverrun(-300.0)).isFalse();
        assertThat(DprReportMetricsCalculator.isCostOverrun(0.0)).isFalse();
    }
    @Test void formats_number_for_whitelist_with_grouping_no_decimals() {
        assertThat(DprReportMetricsCalculator.fmtNumber(1234567.0)).isEqualTo("1,234,567");
        assertThat(DprReportMetricsCalculator.fmtNumber(96.7)).isEqualTo("97");
    }
}
