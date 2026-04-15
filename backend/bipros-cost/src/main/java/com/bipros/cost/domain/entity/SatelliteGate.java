package com.bipros.cost.domain.entity;

/**
 * Satellite-derived payment gate applied to an {@link RaBill} pre-approval.
 *
 * <ul>
 *   <li>PASS — AI progress matches contractor claim within tolerance (≤5%).</li>
 *   <li>HOLD_VARIANCE — variance 5-10% → hold for review.</li>
 *   <li>RED_VARIANCE — variance &gt;10% → certification blocked.</li>
 *   <li>HOLD_SATELLITE_DISPUTE — contractor disputes AI reading.</li>
 * </ul>
 */
public enum SatelliteGate {
    PASS,
    HOLD_VARIANCE,
    RED_VARIANCE,
    HOLD_SATELLITE_DISPUTE
}
