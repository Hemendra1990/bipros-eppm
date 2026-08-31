package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EquipmentOwnership {
    OWNED,
    HIRED,
    SUBCONTRACTOR;

    @JsonCreator
    public static EquipmentOwnership fromString(String value) {
        if (value == null || value.isBlank()) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "OWNED", "OWN", "COMPANY" -> OWNED;
            case "HIRED", "HIRE", "RENTED" -> HIRED;
            case "SUBCONTRACTOR", "SUB", "SUB_CONTRACTOR", "SC" -> SUBCONTRACTOR;
            default -> throw new IllegalArgumentException(
                "Unknown EquipmentOwnership '" + value + "' (valid: OWNED, HIRED, SUBCONTRACTOR)");
        };
    }
}
