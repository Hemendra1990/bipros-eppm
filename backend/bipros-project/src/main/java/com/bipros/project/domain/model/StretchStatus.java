package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Stretch execution status per PMS MasterData Screen 06.
 */
public enum StretchStatus {
    NOT_STARTED,
    ACTIVE,
    COMPLETE,
    SNAGGING;

    @JsonCreator
    public static StretchStatus fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "NOT_STARTED", "PENDING" -> NOT_STARTED;
            case "ACTIVE", "IN_PROGRESS" -> ACTIVE;
            case "COMPLETE", "COMPLETED" -> COMPLETE;
            case "SNAGGING", "PUNCH_LIST" -> SNAGGING;
            default -> throw new IllegalArgumentException(
                "Unknown StretchStatus '" + value
                    + "' (valid: NOT_STARTED, ACTIVE, COMPLETE, SNAGGING)");
        };
    }
}
