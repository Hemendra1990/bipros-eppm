package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum IssueSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    @JsonCreator
    public static IssueSeverity fromString(String value) {
        if (value == null || value.isBlank()) return MEDIUM;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "LOW", "MINOR" -> LOW;
            case "MEDIUM", "MED", "NORMAL" -> MEDIUM;
            case "HIGH", "MAJOR" -> HIGH;
            case "CRITICAL", "BLOCKER", "SEVERE" -> CRITICAL;
            default -> throw new IllegalArgumentException(
                "Unknown IssueSeverity '" + value + "' (valid: LOW, MEDIUM, HIGH, CRITICAL)");
        };
    }
}
