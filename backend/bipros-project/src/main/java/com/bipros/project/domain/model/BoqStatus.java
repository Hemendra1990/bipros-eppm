package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Lifecycle status of a BOQ line item per PMS MasterData Screen 03. The service derives it
 * automatically from {@code percentComplete} (0 → PENDING, in-progress → ACTIVE, 100 →
 * COMPLETED); {@link #ON_HOLD} is the only manual-only transition.
 */
public enum BoqStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    ON_HOLD;

    @JsonCreator
    public static BoqStatus fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "PENDING", "NOT_STARTED" -> PENDING;
            case "ACTIVE", "IN_PROGRESS" -> ACTIVE;
            case "COMPLETED", "COMPLETE", "DONE" -> COMPLETED;
            case "ON_HOLD", "HOLD", "SUSPENDED" -> ON_HOLD;
            default -> throw new IllegalArgumentException(
                "Unknown BoqStatus '" + value + "' (valid: PENDING, ACTIVE, COMPLETED, ON_HOLD)");
        };
    }
}
