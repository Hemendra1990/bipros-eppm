package com.bipros.resource.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Category of a {@link MaterialSource} per PMS MasterData Screen 08. Drives the auto-code
 * prefix (BA / QRY / BD / CEM) and which fields apply (borrow areas have CBR/MDD, quarries
 * have aggregate properties, bitumen depots track supplier only, etc.).
 */
public enum MaterialSourceType {
    BORROW_AREA,
    QUARRY,
    BITUMEN_DEPOT,
    CEMENT_SOURCE;

    @JsonCreator
    public static MaterialSourceType fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "BORROW_AREA", "BA", "BORROW" -> BORROW_AREA;
            case "QUARRY", "QRY", "AGGREGATE" -> QUARRY;
            case "BITUMEN_DEPOT", "BD", "BITUMEN" -> BITUMEN_DEPOT;
            case "CEMENT_SOURCE", "CEMENT", "CEM" -> CEMENT_SOURCE;
            default -> throw new IllegalArgumentException(
                "Unknown MaterialSourceType '" + value
                    + "' (valid: BORROW_AREA, QUARRY, BITUMEN_DEPOT, CEMENT_SOURCE)");
        };
    }

    /** Code prefix used when auto-generating source IDs. */
    public String prefix() {
        return switch (this) {
            case BORROW_AREA -> "BA";
            case QUARRY -> "QRY";
            case BITUMEN_DEPOT -> "BD";
            case CEMENT_SOURCE -> "CEM";
        };
    }
}
