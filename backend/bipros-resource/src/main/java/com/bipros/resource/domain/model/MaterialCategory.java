package com.bipros.resource.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Material category per PMS MasterData Screen 09a. The UI dropdown exposes these 8 values.
 */
public enum MaterialCategory {
    BITUMINOUS,
    AGGREGATE,
    CEMENT,
    STEEL,
    GRANULAR,
    SAND,
    PRECAST,
    ROAD_MARKING;

    @JsonCreator
    public static MaterialCategory fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "BITUMINOUS", "BITUMEN" -> BITUMINOUS;
            case "AGGREGATE", "AGGREGATES" -> AGGREGATE;
            case "CEMENT", "OPC", "PPC" -> CEMENT;
            case "STEEL", "REBAR", "STRUCTURAL_STEEL" -> STEEL;
            case "GRANULAR", "GSB", "WMM" -> GRANULAR;
            case "SAND", "FINE_AGGREGATE" -> SAND;
            case "PRECAST", "PRECAST_CONCRETE" -> PRECAST;
            case "ROAD_MARKING", "PAINT", "THERMOPLASTIC" -> ROAD_MARKING;
            default -> throw new IllegalArgumentException(
                "Unknown MaterialCategory '" + value + "'");
        };
    }
}
