package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EquipmentAvailability {
    AVAILABLE,
    UTILIZED,
    IDLE,
    BREAKDOWN;

    @JsonCreator
    public static EquipmentAvailability fromString(String value) {
        if (value == null || value.isBlank()) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "AVAILABLE", "READY", "STANDBY" -> AVAILABLE;
            case "UTILIZED", "WORKING", "IN_USE", "INUSE" -> UTILIZED;
            case "IDLE", "WAITING" -> IDLE;
            case "BREAKDOWN", "DOWN", "MAINTENANCE", "REPAIR" -> BREAKDOWN;
            default -> throw new IllegalArgumentException(
                "Unknown EquipmentAvailability '" + value + "' (valid: AVAILABLE, UTILIZED, IDLE, BREAKDOWN)");
        };
    }
}
