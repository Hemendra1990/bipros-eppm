package com.bipros.ai.agent.core;

/**
 * Finding severity, ordered least-to-most severe so {@link #ordinal()} doubles as a rank
 * for {@code minSeverity} filtering (INFO=0 … CRITICAL=4). Deterministic — assigned by an
 * agent's rule thresholds and never changed by the LLM narrator.
 */
public enum Severity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** True when this severity is at least as severe as {@code min}. */
    public boolean atLeast(Severity min) {
        return min == null || this.ordinal() >= min.ordinal();
    }

    /** Null-safe parse; unknown/blank input falls back to {@link #INFO}. */
    public static Severity fromString(String raw) {
        if (raw == null || raw.isBlank()) return INFO;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return INFO;
        }
    }
}
