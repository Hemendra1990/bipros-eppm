package com.bipros.evm.domain.algorithm;

import com.bipros.activity.domain.model.Activity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * EV = BAC × (% complete / 100).
 * Most common P6 technique — uses the activity's reported percent complete.
 */
public class ActivityPercentCompleteStrategy implements EvmTechniqueStrategy {

    private static final int SCALE = 4;

    @Override
    public BigDecimal calculateEarnedValue(Activity activity, BigDecimal bac, BigDecimal pv) {
        Double pct = activity.getPercentComplete();
        if (pct == null || bac == null) {
            return BigDecimal.ZERO;
        }
        return bac.multiply(BigDecimal.valueOf(pct))
                .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
    }
}
