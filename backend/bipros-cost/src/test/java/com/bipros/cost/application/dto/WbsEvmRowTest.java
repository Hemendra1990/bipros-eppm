package com.bipros.cost.application.dto;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class WbsEvmRowTest {
    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @Test
    void per_node_evm_is_derived_from_bac_ev_pv_ac() {
        // node BAC 10,000,000; EV 2,400,000; PV 3,000,000; AC 1,000,000
        WbsEvmRow r = WbsEvmRow.of("EARTH", "Earthwork",
                bd("10000000"), bd("2400000"), bd("3000000"), bd("1000000"));
        assertThat(r.bac()).isEqualByComparingTo("10000000");
        assertThat(r.earnedValue()).isEqualByComparingTo("2400000");
        assertThat(r.plannedValue()).isEqualByComparingTo("3000000");
        assertThat(r.actualCost()).isEqualByComparingTo("1000000");
        assertThat(r.costVariance()).isEqualByComparingTo("1400000");           // EV - AC
        assertThat(r.scheduleVariance()).isEqualByComparingTo("-600000");        // EV - PV
        assertThat(r.costPerformanceIndex()).isEqualByComparingTo("2.4000");     // EV / AC
        assertThat(r.schedulePerformanceIndex()).isEqualByComparingTo("0.8000"); // EV / PV
        assertThat(r.estimateAtCompletion()).isEqualByComparingTo("4166666.67"); // BAC / CPI
        assertThat(r.varianceAtCompletion()).isEqualByComparingTo("5833333.33"); // BAC - EAC
    }

    @Test
    void zero_actual_yields_null_cpi_and_eac_falls_back_to_bac() {
        WbsEvmRow r = WbsEvmRow.of("X", "x", bd("10000000"), bd("0"), bd("0"), bd("0"));
        assertThat(r.costPerformanceIndex()).isNull();
        assertThat(r.estimateAtCompletion()).isEqualByComparingTo("10000000");
        assertThat(r.schedulePerformanceIndex()).isNull();
    }
}
