package com.bipros.evm.domain.algorithm;

import com.bipros.activity.domain.model.Activity;

import java.math.BigDecimal;

/**
 * EV = 0 until activity is 100% complete, then EV = BAC.
 * Conservative technique for short-duration activities.
 */
public class ZeroOneHundredStrategy implements EvmTechniqueStrategy {

    @Override
    public BigDecimal calculateEarnedValue(Activity activity, BigDecimal bac, BigDecimal pv) {
        Double pct = activity.getPercentComplete();
        if (pct != null && pct >= 100.0) {
            return bac != null ? bac : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }
}
