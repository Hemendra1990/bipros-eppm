package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Lifecycle status of a BOQ line item per PMS MasterData Screen 03. The service derives it
 * automatically from {@code percentComplete} and {@code qtyExecutedToDate vs boqQty} —
 * {@link #ON_HOLD} is the only manual-only transition.
 *
 * <ul>
 *   <li>{@link #PENDING} — pct = 0 / null and no overrun.</li>
 *   <li>{@link #ACTIVE} — 0 &lt; pct &lt; 100 % and no overrun.</li>
 *   <li>{@link #COMPLETED} — pct ≥ 100 % AND qtyExecutedToDate ≤ boqQty.</li>
 *   <li>{@link #OVERRUN} — qtyExecutedToDate &gt; boqQty (the unbillable-without-VO state).</li>
 *   <li>{@link #ON_HOLD} — manual override, sticky until cleared.</li>
 * </ul>
 */
public enum BoqStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    OVERRUN,
    ON_HOLD;

    @JsonCreator
    public static BoqStatus fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "PENDING", "NOT_STARTED" -> PENDING;
            case "ACTIVE", "IN_PROGRESS" -> ACTIVE;
            case "COMPLETED", "COMPLETE", "DONE" -> COMPLETED;
            case "OVERRUN", "EXCEEDED", "OVER" -> OVERRUN;
            case "ON_HOLD", "HOLD", "SUSPENDED" -> ON_HOLD;
            default -> throw new IllegalArgumentException(
                "Unknown BoqStatus '" + value + "' (valid: PENDING, ACTIVE, COMPLETED, OVERRUN, ON_HOLD)");
        };
    }
}
