package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Shift {
    DAY,
    NIGHT;

    @JsonCreator
    public static Shift fromString(String value) {
        if (value == null || value.isBlank()) return null;
        String n = value.trim().toUpperCase();
        return switch (n) {
            case "DAY", "GENERAL", "1ST", "FIRST" -> DAY;
            case "NIGHT", "2ND", "SECOND" -> NIGHT;
            default -> throw new IllegalArgumentException(
                "Unknown Shift '" + value + "' (valid: DAY, NIGHT)");
        };
    }
}
