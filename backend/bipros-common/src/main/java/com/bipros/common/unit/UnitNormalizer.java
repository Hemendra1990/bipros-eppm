package com.bipros.common.unit;

import java.util.Locale;
import java.util.Map;

/**
 * Collapses unit-of-measure synonyms (e.g. {@code cu.m.}, {@code CUM}, {@code m3}) to one
 * canonical spelling, and compares two raw unit strings for equivalence. Pure Java, no
 * dependencies — the foundation for the unit-consistency repair endpoint, which anchors
 * every unit in the Work Activity → Productivity Norm → Activity → DPR → BOQ chain to a
 * single canonical spelling per measure.
 *
 * <p>Normalization is meaning-preserving only: it merges known spelling variants of the
 * <em>same</em> measure, and never merges distinct measures (e.g. {@code mm} is never
 * merged into {@code m}).
 */
public final class UnitNormalizer {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            // Cum
            Map.entry("cu.m.", "Cum"),
            Map.entry("cum", "Cum"),
            Map.entry("cu_m", "Cum"),
            Map.entry("m3", "Cum"),
            Map.entry("cubic metre", "Cum"),
            Map.entry("cubic meter", "Cum"),

            // Sqm
            Map.entry("sq.m.", "Sqm"),
            Map.entry("sqm", "Sqm"),
            Map.entry("m2", "Sqm"),
            Map.entry("square metre", "Sqm"),
            Map.entry("square meter", "Sqm"),

            // m (running metre)
            Map.entry("lin.m.", "m"),
            Map.entry("linm", "m"),
            Map.entry("lm", "m"),
            Map.entry("rm", "m"),
            Map.entry("r/mtr", "m"),
            Map.entry("running metre", "m"),
            Map.entry("running meter", "m"),

            // mm — distinct measure, self only (must never merge into "m")
            Map.entry("mm", "mm"),

            // Nos
            Map.entry("nr.", "Nos"),
            Map.entry("nr", "Nos"),
            Map.entry("no.", "Nos"),
            Map.entry("number", "Nos"),
            Map.entry("nos", "Nos"),

            // Day
            Map.entry("manday", "Day"),
            Map.entry("man-day", "Day"),
            Map.entry("mandays", "Day"),

            // L
            Map.entry("ltr", "L"),
            Map.entry("ltr (legacy)", "L"),
            Map.entry("litre", "L"),
            Map.entry("liter", "L"),

            // LS
            Map.entry("ls", "LS"),
            Map.entry("lump sum", "LS"),

            // MT
            Map.entry("tonne", "MT"),
            Map.entry("t", "MT"),
            Map.entry("metric tonne", "MT"),

            // kg
            Map.entry("kg.", "kg"),

            // self-canonical
            Map.entry("km", "Km"),
            Map.entry("trip", "trip"),
            Map.entry("layer", "layer"),
            Map.entry("sqft", "Sqft"),
            Map.entry("ft", "ft"),
            Map.entry("in", "in"),
            Map.entry("brass", "Brass")
    );

    private UnitNormalizer() {}

    /**
     * Canonical spelling for a raw unit label. Trims, then maps known synonyms to one
     * canonical label (matched case-insensitively). Unknown units pass through trimmed,
     * with their original spelling preserved. {@code null}/blank input returns {@code null}.
     */
    public static String canonicalLabel(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String canonical = ALIASES.get(trimmed.toLowerCase(Locale.ROOT));
        return canonical != null ? canonical : trimmed;
    }

    /**
     * True when two raw unit strings denote the same measure, compared via
     * {@link #canonicalLabel(String)} case-insensitively. If either side is null/blank
     * (unknown), returns {@code false} — unknown is never "same" as anything.
     */
    public static boolean sameUnit(String a, String b) {
        String canonicalA = canonicalLabel(a);
        String canonicalB = canonicalLabel(b);
        if (canonicalA == null || canonicalB == null) {
            return false;
        }
        return canonicalA.equalsIgnoreCase(canonicalB);
    }
}
