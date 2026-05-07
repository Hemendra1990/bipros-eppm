package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SafetyIncidentType {
    NONE,
    NEAR_MISS,
    INCIDENT;

    @JsonCreator
    public static SafetyIncidentType fromString(String value) {
        if (value == null || value.isBlank()) return NONE;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "NONE", "NIL", "CLEAR" -> NONE;
            case "NEAR_MISS", "NEARMISS", "NM" -> NEAR_MISS;
            case "INCIDENT", "ACCIDENT", "INJURY" -> INCIDENT;
            default -> throw new IllegalArgumentException(
                "Unknown SafetyIncidentType '" + value + "' (valid: NONE, NEAR_MISS, INCIDENT)");
        };
    }
}
