package com.bipros.evm.domain.algorithm;

import com.bipros.activity.domain.model.Activity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * EV = BAC × 0.5 when activity starts, EV = BAC when 100% complete.
 * Common for activities where detailed progress measurement is impractical.
 */
public class FiftyFiftyStrategy implements EvmTechniqueStrategy {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    @Override
    public BigDecimal calculateEarnedValue(Activity activity, BigDecimal bac, BigDecimal pv) {
        if (bac == null) {
            return BigDecimal.ZERO;
        }
        Double pct = activity.getPercentComplete();

        // 100% complete → full BAC
        if (pct != null && pct >= 100.0) {
            return bac;
        }

        // In progress (started but not complete) → 50% of BAC
        if (activity.getActualStartDate() != null || (pct != null && pct > 0)) {
            return bac.multiply(HALF).setScale(2, RoundingMode.HALF_UP);
        }

        // Not started
        return BigDecimal.ZERO;
    }
}
