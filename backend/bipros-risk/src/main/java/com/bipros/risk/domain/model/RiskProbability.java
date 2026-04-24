package com.bipros.risk.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Five-point probability scale. Accepts either the qualitative name (VERY_LOW…VERY_HIGH),
 * the ordinal 1-5, or a decimal probability between 0 and 1 (mapped to the nearest bucket).
 */
public enum RiskProbability {
    VERY_LOW(1),
    LOW(2),
    MEDIUM(3),
    HIGH(4),
    VERY_HIGH(5);

    private final int value;

    RiskProbability(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @JsonCreator
    public static RiskProbability fromAny(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) {
            double d = n.doubleValue();
            if (d >= 0 && d <= 1) {
                // Decimal probability → 1..5 bucket
                if (d < 0.2) return VERY_LOW;
                if (d < 0.4) return LOW;
                if (d < 0.6) return MEDIUM;
                if (d < 0.8) return HIGH;
                return VERY_HIGH;
            }
            int ordinal = (int) Math.round(d);
            return switch (ordinal) {
                case 1 -> VERY_LOW;
                case 2 -> LOW;
                case 3 -> MEDIUM;
                case 4 -> HIGH;
                case 5 -> VERY_HIGH;
                default -> throw new IllegalArgumentException(
                    "RiskProbability numeric value must be 1-5 or 0.0-1.0, got: " + raw);
            };
        }
        return RiskProbability.valueOf(raw.toString().trim().toUpperCase());
    }
}
