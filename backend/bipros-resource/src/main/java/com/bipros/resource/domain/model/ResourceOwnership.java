package com.bipros.resource.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Equipment ownership model per PMS MasterData Screen 04. Drives hire-rate applicability in
 * the cost module.
 */
public enum ResourceOwnership {
    OWNED,
    HIRED,
    SUB_CONTRACTOR_PROVIDED;

    @JsonCreator
    public static ResourceOwnership fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "OWNED", "OWN" -> OWNED;
            case "HIRED", "HIRE", "RENTAL", "RENTED" -> HIRED;
            case "SUB_CONTRACTOR_PROVIDED", "SUBCONTRACTOR_PROVIDED", "SUBCONTRACTED", "SUB" ->
                SUB_CONTRACTOR_PROVIDED;
            default -> throw new IllegalArgumentException(
                "Unknown ResourceOwnership '" + value
                    + "' (valid: OWNED, HIRED, SUB_CONTRACTOR_PROVIDED)");
        };
    }
}
