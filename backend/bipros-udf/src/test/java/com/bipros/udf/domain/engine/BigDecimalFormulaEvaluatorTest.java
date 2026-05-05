package com.bipros.udf.domain.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BigDecimalFormulaEvaluator — EVM formula validation")
class BigDecimalFormulaEvaluatorTest {

    private static BigDecimal eval(String expression, Map<String, BigDecimal> context) {
        return new BigDecimalFormulaEvaluator(expression, context, 4, RoundingMode.HALF_UP, BigDecimal.ZERO)
                .evaluate().value();
    }

    @Test
    @DisplayName("EVM_CPI: EV / AC")
    void evmCpi() {
        Map<String, BigDecimal> ctx = Map.of("EV", bd(80), "AC", bd(100));
        assertThat(eval("IF($AC = 0, 0, $EV / $AC)", ctx))
                .isEqualByComparingTo(bd("0.8"));
    }

    @Test
    @DisplayName("EVM_SPI: EV / PV")
    void evmSpi() {
        Map<String, BigDecimal> ctx = Map.of("EV", bd(80), "PV", bd(100));
        assertThat(eval("IF($PV = 0, 0, $EV / $PV)", ctx))
                .isEqualByComparingTo(bd("0.8"));
    }

    @Test
    @DisplayName("EVM_SV: EV - PV")
    void evmSv() {
        Map<String, BigDecimal> ctx = Map.of("EV", bd(80), "PV", bd(100));
        assertThat(eval("$EV - $PV", ctx))
                .isEqualByComparingTo(bd("-20"));
    }

    @Test
    @DisplayName("EVM_CV: EV - AC")
    void evmCv() {
        Map<String, BigDecimal> ctx = Map.of("EV", bd(80), "AC", bd(100));
        assertThat(eval("$EV - $AC", ctx))
                .isEqualByComparingTo(bd("-20"));
    }

    @Test
    @DisplayName("EVM_EAC_CPI: BAC / CPI")
    void evmEacCpi() {
        Map<String, BigDecimal> ctx = Map.of("BAC", bd(1000), "CPI", bd("0.8"));
        assertThat(eval("IF($CPI = 0, $BAC, $BAC / $CPI)", ctx))
                .isEqualByComparingTo(bd("1250"));
    }

    @Test
    @DisplayName("EVM_TCPI: (BAC - EV) / (EAC - AC)")
    void evmTcpi() {
        Map<String, BigDecimal> ctx = Map.of(
                "BAC", bd(1000),
                "EV", bd(400),
                "EAC", bd(1250),
                "AC", bd(500));
        assertThat(eval("IF($EAC - $AC = 0, 0, ($BAC - $EV) / ($EAC - $AC))", ctx))
                .isEqualByComparingTo(bd("0.8"));
    }

    @Test
    @DisplayName("EVM_PERF_PCT: (EV / BAC) * 100")
    void evmPerfPct() {
        Map<String, BigDecimal> ctx = Map.of("EV", bd(400), "BAC", bd(1000));
        assertThat(eval("IF($BAC = 0, 0, ($EV / $BAC) * 100)", ctx))
                .isEqualByComparingTo(bd("40"));
    }

    @Test
    @DisplayName("SCHED_HEALTH_SCORE clamped to 0-100")
    void scheduleHealthScore() {
        Map<String, BigDecimal> ctx = Map.of(
                "CRITICAL_PCT", bd("0.2"),
                "NEAR_CRITICAL_PCT", bd("0.1"),
                "DURATION_VARIANCE", bd("0.05"));
        BigDecimal result = eval("MAX(0, MIN(100, 100 - ($CRITICAL_PCT * 40) - ($NEAR_CRITICAL_PCT * 20) - MAX(0, $DURATION_VARIANCE * 40)))", ctx);
        assertThat(result).isEqualByComparingTo(bd("88"));
    }

    @Test
    @DisplayName("BOQ_PCT_COMPLETE: qty executed / boq qty")
    void boqPctComplete() {
        Map<String, BigDecimal> ctx = Map.of("QTY_EXECUTED", bd(75), "BOQ_QTY", bd(100));
        assertThat(eval("IF($BOQ_QTY = 0, 0, ($QTY_EXECUTED / $BOQ_QTY) * 100)", ctx))
                .isEqualByComparingTo(bd("75"));
    }

    @Test
    @DisplayName("Division by zero returns zero-default")
    void divisionByZero() {
        Map<String, BigDecimal> ctx = Map.of("EV", bd(80), "AC", bd(0));
        assertThat(eval("$EV / $AC", ctx)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("MAX and MIN functions")
    void maxMinFunctions() {
        assertThat(eval("MAX(5, 10, 3)", Map.of())).isEqualByComparingTo(bd("10"));
        assertThat(eval("MIN(5, 10, 3)", Map.of())).isEqualByComparingTo(bd("3"));
    }

    @Test
    @DisplayName("POWER and SQRT functions")
    void powerSqrtFunctions() {
        assertThat(eval("POWER(2, 3)", Map.of())).isEqualByComparingTo(bd("8"));
        assertThat(eval("SQRT(16)", Map.of())).isEqualByComparingTo(bd("4"));
    }

    @Test
    @DisplayName("SUM function")
    void sumFunction() {
        assertThat(eval("SUM(10, 20, 30)", Map.of())).isEqualByComparingTo(bd("60"));
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }

    private static BigDecimal bd(int val) {
        return BigDecimal.valueOf(val);
    }
}
