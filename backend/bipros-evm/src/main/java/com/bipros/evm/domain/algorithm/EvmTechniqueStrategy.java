package com.bipros.evm.domain.algorithm;

import com.bipros.activity.domain.model.Activity;

import java.math.BigDecimal;

/**
 * Strategy interface for computing Earned Value (EV) for a single activity.
 * Each implementation corresponds to one of the five P6 EVM techniques.
 */
public interface EvmTechniqueStrategy {

    /**
     * Calculate earned value for a single activity.
     *
     * @param activity the activity
     * @param bac      Budget At Completion for this activity (cost-based)
     * @param pv       Planned Value for this activity (cost-based)
     * @return the earned value
     */
    BigDecimal calculateEarnedValue(Activity activity, BigDecimal bac, BigDecimal pv);
}
