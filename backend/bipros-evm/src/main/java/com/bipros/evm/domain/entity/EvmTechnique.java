package com.bipros.evm.domain.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Earned value technique. Accepts common aliases so dashboards can use the shorthand P6 forms.
 */
public enum EvmTechnique {
    ACTIVITY_PERCENT_COMPLETE,
    ZERO_ONE_HUNDRED,
    FIFTY_FIFTY,
    WEIGHTED_STEPS,
    LEVEL_OF_EFFORT;

    @JsonCreator
    public static EvmTechnique fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "PERCENT_COMPLETE", "ACTIVITY_PERCENT_COMPLETE", "PCT_COMPLETE" -> ACTIVITY_PERCENT_COMPLETE;
            case "ZERO_ONE_HUNDRED", "0_100", "ZERO_HUNDRED" -> ZERO_ONE_HUNDRED;
            case "FIFTY_FIFTY", "50_50" -> FIFTY_FIFTY;
            case "WEIGHTED_STEPS", "MILESTONE_WEIGHTED" -> WEIGHTED_STEPS;
            case "LEVEL_OF_EFFORT", "LOE" -> LEVEL_OF_EFFORT;
            default -> throw new IllegalArgumentException(
                "Unknown EvmTechnique '" + value
                    + "' (valid: ACTIVITY_PERCENT_COMPLETE, ZERO_ONE_HUNDRED, FIFTY_FIFTY, WEIGHTED_STEPS, LEVEL_OF_EFFORT)");
        };
    }
}
