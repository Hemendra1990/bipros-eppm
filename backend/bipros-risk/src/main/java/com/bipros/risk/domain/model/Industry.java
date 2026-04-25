package com.bipros.risk.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Broad industry tag for risk-library entries. Each {@link RiskTemplate} carries one
 * industry plus zero or more {@code applicableProjectCategories} (matching the project
 * module's {@code ProjectCategory} enum names) so the "Add from Library" modal can
 * pre-filter to risks relevant for the current project.
 */
public enum Industry {
    ROAD,
    BRIDGE,
    BUILDING,
    CONSTRUCTION_GENERAL,
    REFINERY,
    OIL_GAS,
    RAILWAY,
    METRO,
    POWER,
    WATER,
    IT,
    GENERIC;

    @JsonCreator
    public static Industry fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "ROAD", "ROADS", "HIGHWAY_PROJECT" -> ROAD;
            case "BRIDGE", "BRIDGES" -> BRIDGE;
            case "BUILDING", "BUILDINGS" -> BUILDING;
            case "CONSTRUCTION", "CONSTRUCTION_GENERAL", "GENERAL_CONSTRUCTION" -> CONSTRUCTION_GENERAL;
            case "REFINERY", "REFINING" -> REFINERY;
            case "OIL_GAS", "OIL", "GAS", "OILGAS" -> OIL_GAS;
            case "RAILWAY", "RAIL" -> RAILWAY;
            case "METRO", "METRO_RAIL" -> METRO;
            case "POWER", "POWER_PLANT" -> POWER;
            case "WATER", "WATER_PROJECT" -> WATER;
            case "IT", "INFORMATION_TECHNOLOGY", "SOFTWARE" -> IT;
            case "GENERIC", "ANY", "OTHER" -> GENERIC;
            default -> throw new IllegalArgumentException(
                "Unknown Industry '" + value + "' (valid: ROAD, BRIDGE, BUILDING, "
                    + "CONSTRUCTION_GENERAL, REFINERY, OIL_GAS, RAILWAY, METRO, POWER, WATER, IT, GENERIC)");
        };
    }
}
