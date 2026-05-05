package com.bipros.udf.domain.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BigDecimalFormulaEvaluator — debug nested MAX/MIN")
class BigDecimalFormulaEvaluatorDebugTest {

    private static BigDecimal eval(String expression, Map<String, BigDecimal> context) {
        return new BigDecimalFormulaEvaluator(expression, context, 4, RoundingMode.HALF_UP, BigDecimal.ZERO)
                .evaluate().value();
    }

    @Test
    @DisplayName("Simple MAX")
    void simpleMax() {
        assertThat(eval("MAX(5, 10)", Map.of())).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    @DisplayName("Nested MAX/MIN")
    void nestedMaxMin() {
        assertThat(eval("MAX(0, MIN(100, 50))", Map.of())).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    @DisplayName("Nested MAX with subtraction inside MIN")
    void nestedWithSubtraction() {
        assertThat(eval("MAX(0, MIN(100, 100 - 8 - 2 - 2))", Map.of()))
                .isEqualByComparingTo(new BigDecimal("88"));
    }

    @Test
    @DisplayName("Nested MAX with variable")
    void nestedWithVariable() {
        assertThat(eval("MAX(0, $DURATION_VARIANCE * 40)", Map.of("DURATION_VARIANCE", new BigDecimal("0.05"))))
                .isEqualByComparingTo(new BigDecimal("2"));
    }

    @Test
    @DisplayName("Full health score expression")
    void fullHealthScore() {
        Map<String, BigDecimal> ctx = Map.of(
                "CRITICAL_PCT", new BigDecimal("0.2"),
                "NEAR_CRITICAL_PCT", new BigDecimal("0.1"),
                "DURATION_VARIANCE", new BigDecimal("0.05"));
        assertThat(eval("MAX(0, MIN(100, 100 - ($CRITICAL_PCT * 40) - ($NEAR_CRITICAL_PCT * 20) - MAX(0, $DURATION_VARIANCE * 40)))", ctx))
                .isEqualByComparingTo(new BigDecimal("88"));
    }

    @Test
    @DisplayName("Health score inner expression")
    void healthScoreInner() {
        Map<String, BigDecimal> ctx = Map.of(
                "CRITICAL_PCT", new BigDecimal("0.2"),
                "NEAR_CRITICAL_PCT", new BigDecimal("0.1"),
                "DURATION_VARIANCE", new BigDecimal("0.05"));
        assertThat(eval("100 - ($CRITICAL_PCT * 40) - ($NEAR_CRITICAL_PCT * 20) - MAX(0, $DURATION_VARIANCE * 40)", ctx))
                .isEqualByComparingTo(new BigDecimal("88"));
    }

    @Test
    @DisplayName("MIN with inner expression")
    void minWithInnerExpression() {
        Map<String, BigDecimal> ctx = Map.of(
                "CRITICAL_PCT", new BigDecimal("0.2"),
                "NEAR_CRITICAL_PCT", new BigDecimal("0.1"),
                "DURATION_VARIANCE", new BigDecimal("0.05"));
        assertThat(eval("MIN(100, 100 - ($CRITICAL_PCT * 40) - ($NEAR_CRITICAL_PCT * 20) - MAX(0, $DURATION_VARIANCE * 40))", ctx))
                .isEqualByComparingTo(new BigDecimal("88"));
    }
}
