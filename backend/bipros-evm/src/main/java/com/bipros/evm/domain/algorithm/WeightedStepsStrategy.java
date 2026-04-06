package com.bipros.evm.domain.algorithm;

import com.bipros.activity.domain.model.Activity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * EV = BAC × (completed step weight / total step weight).
 * Since step weights aren't modeled yet, this falls back to physical percent complete
 * which represents the weighted progress reported by the field.
 */
public class WeightedStepsStrategy implements EvmTechniqueStrategy {

    private static final int SCALE = 4;

    @Override
    public BigDecimal calculateEarnedValue(Activity activity, BigDecimal bac, BigDecimal pv) {
        if (bac == null) {
            return BigDecimal.ZERO;
        }
        // Prefer physicalPercentComplete for weighted steps (field-reported progress)
        Double pct = activity.getPhysicalPercentComplete();
        if (pct == null) {
            pct = activity.getPercentComplete();
        }
        if (pct == null) {
            return BigDecimal.ZERO;
        }
        return bac.multiply(BigDecimal.valueOf(pct))
                .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
    }
}
