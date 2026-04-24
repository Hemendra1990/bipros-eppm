package com.bipros.resource.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MaterialStatus {
    ACTIVE, INACTIVE, DISCONTINUED;

    @JsonCreator
    public static MaterialStatus fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "ACTIVE" -> ACTIVE;
            case "INACTIVE" -> INACTIVE;
            case "DISCONTINUED", "RETIRED" -> DISCONTINUED;
            default -> throw new IllegalArgumentException(
                "Unknown MaterialStatus '" + value + "' (valid: ACTIVE, INACTIVE, DISCONTINUED)");
        };
    }
}
