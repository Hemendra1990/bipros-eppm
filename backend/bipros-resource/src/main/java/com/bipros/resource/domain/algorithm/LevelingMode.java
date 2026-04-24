package com.bipros.resource.domain.algorithm;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Levelling strategy for resource balancing.
 * <ul>
 *   <li>{@link #LEVEL_WITHIN_FLOAT} — push activities within their total float only (default).</li>
 *   <li>{@link #LEVEL_ALL} — level aggressively, allowing project-finish slip.</li>
 *   <li>{@link #SMOOTH} — minimise day-to-day resource spikes without extending the finish.</li>
 * </ul>
 * Accepts common P6 aliases: {@code AUTOMATIC}/{@code AUTO} → {@link #LEVEL_WITHIN_FLOAT}.
 */
public enum LevelingMode {
    LEVEL_WITHIN_FLOAT,
    LEVEL_ALL,
    SMOOTH;

    @JsonCreator
    public static LevelingMode fromString(String value) {
        if (value == null) return null;
        String normalised = value.trim().toUpperCase();
        return switch (normalised) {
            case "AUTOMATIC", "AUTO", "LEVEL_WITHIN_FLOAT", "WITHIN_FLOAT" -> LEVEL_WITHIN_FLOAT;
            case "LEVEL_ALL", "ALL", "AGGRESSIVE" -> LEVEL_ALL;
            case "SMOOTH", "SMOOTHING" -> SMOOTH;
            default -> throw new IllegalArgumentException(
                "Unknown LevelingMode: '" + value + "' (valid: LEVEL_WITHIN_FLOAT, LEVEL_ALL, SMOOTH)");
        };
    }
}
