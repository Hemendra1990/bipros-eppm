package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ManpowerCategory {
    SKILLED,
    SEMI_SKILLED,
    UNSKILLED;

    @JsonCreator
    public static ManpowerCategory fromString(String value) {
        if (value == null || value.isBlank()) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "SKILLED", "S" -> SKILLED;
            case "SEMI_SKILLED", "SEMISKILLED", "SS" -> SEMI_SKILLED;
            case "UNSKILLED", "U" -> UNSKILLED;
            default -> throw new IllegalArgumentException(
                "Unknown ManpowerCategory '" + value + "' (valid: SKILLED, SEMI_SKILLED, UNSKILLED)");
        };
    }
}
