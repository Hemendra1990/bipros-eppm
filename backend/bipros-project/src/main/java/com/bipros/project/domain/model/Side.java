package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Carriageway side for a chainage band on a road/highway DPR row. */
public enum Side {
    LHS,
    RHS,
    CENTER;

    @JsonCreator
    public static Side fromString(String value) {
        if (value == null || value.isBlank()) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "LHS", "LEFT" -> LHS;
            case "RHS", "RIGHT" -> RHS;
            case "CENTER", "CENTRE", "MEDIAN", "BOTH" -> CENTER;
            default -> throw new IllegalArgumentException(
                "Unknown Side '" + value + "' (valid: LHS, RHS, CENTER)");
        };
    }
}
