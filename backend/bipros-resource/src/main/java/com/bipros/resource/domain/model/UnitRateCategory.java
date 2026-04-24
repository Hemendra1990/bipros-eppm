package com.bipros.resource.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Coarse category bucket for the Unit Rate Master (Screen 05). Mirrors the four tabs on the
 * PMS MasterData Unit Rate screen — EQUIPMENT / MANPOWER / MATERIAL / SUB_CONTRACT — and is
 * denormalised onto {@code resource_rates} so the rate register can be filtered without a
 * join back to {@code resources}.
 */
public enum UnitRateCategory {
    EQUIPMENT,
    MANPOWER,
    MATERIAL,
    SUB_CONTRACT;

    @JsonCreator
    public static UnitRateCategory fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "EQUIPMENT", "EQP" -> EQUIPMENT;
            case "MANPOWER", "LABOUR", "LABOR" -> MANPOWER;
            case "MATERIAL", "MATERIALS" -> MATERIAL;
            case "SUB_CONTRACT", "SUBCONTRACT", "SC" -> SUB_CONTRACT;
            default -> throw new IllegalArgumentException(
                "Unknown UnitRateCategory '" + value
                    + "' (valid: EQUIPMENT, MANPOWER, MATERIAL, SUB_CONTRACT)");
        };
    }
}
