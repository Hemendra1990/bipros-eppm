package com.bipros.reporting.materialconsumption;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialConsumptionAlertEvaluatorTest {

    @Test
    void negativeBalanceFlagged() {
        List<String> a = MaterialConsumptionAlertEvaluator.evaluate(
            new BigDecimal("5"), new BigDecimal("-1"), new BigDecimal("10"));
        assertThat(a).containsExactly("NEGATIVE_BALANCE");
    }

    @Test
    void missingUnitRateFlaggedWhenConsumedAndRateMissing() {
        assertThat(MaterialConsumptionAlertEvaluator.evaluate(new BigDecimal("5"), null, null))
            .containsExactly("MISSING_UNIT_RATE");
        assertThat(MaterialConsumptionAlertEvaluator.evaluate(new BigDecimal("5"), null, BigDecimal.ZERO))
            .containsExactly("MISSING_UNIT_RATE");
    }

    @Test
    void nullBalanceAndPresentRateAndPositiveConsumed_noAlerts() {
        assertThat(MaterialConsumptionAlertEvaluator.evaluate(
            new BigDecimal("5"), null, new BigDecimal("10"))).isEmpty();
    }

    @Test
    void zeroConsumedNoMissingRateAlert() {
        assertThat(MaterialConsumptionAlertEvaluator.evaluate(
            BigDecimal.ZERO, null, null)).isEmpty();
    }
}
