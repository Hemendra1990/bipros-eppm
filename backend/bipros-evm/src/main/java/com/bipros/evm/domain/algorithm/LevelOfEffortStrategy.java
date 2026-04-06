package com.bipros.evm.domain.algorithm;

import com.bipros.activity.domain.model.Activity;

import java.math.BigDecimal;

/**
 * EV = PV for Level of Effort activities.
 * LOE activities earn value equal to their plan — they are time-based,
 * not output-based (e.g., project management, supervision).
 */
public class LevelOfEffortStrategy implements EvmTechniqueStrategy {

    @Override
    public BigDecimal calculateEarnedValue(Activity activity, BigDecimal bac, BigDecimal pv) {
        return pv != null ? pv : BigDecimal.ZERO;
    }
}
