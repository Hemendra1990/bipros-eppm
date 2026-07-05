package com.bipros.ai.voice.dpr;

import com.bipros.ai.match.LabelMatcher;

import java.util.List;
import java.util.Map;

/**
 * Canonical unit-of-measure codes for the DPR form. The backend has no authoritative unit list, so
 * this mirrors the frontend's single source of truth. The DPR unit dropdown only renders a value
 * cleanly when it is one of these exact codes (e.g. "Cum", not "cubic meter").
 *
 * <p>KEEP IN SYNC WITH {@code frontend/src/lib/constants/units.ts} (STANDARD_UNITS).
 */
final class DprUnits {

  static final List<String> STANDARD_UNITS = List.of(
      "Cum", "Brass", "L", "Sqm", "Sqft", "Rm", "Lm", "lin.m.", "R/mtr", "m", "mm", "ft", "in",
      "kg", "MT", "Tonne", "Quintal", "Bag", "Each", "Nr", "Nos", "Day", "Hour");

  /**
   * Spoken/written variants → canonical code. Keys are stored already-normalized (lowercase,
   * single-spaced) and looked up via {@link LabelMatcher#norm(String)}.
   */
  private static final Map<String, String> SYNONYMS = Map.ofEntries(
      Map.entry("cubic meter", "Cum"), Map.entry("cubic metre", "Cum"),
      Map.entry("cubic meters", "Cum"), Map.entry("cubic metres", "Cum"),
      Map.entry("cubic m", "Cum"), Map.entry("m3", "Cum"),
      Map.entry("square meter", "Sqm"), Map.entry("square metre", "Sqm"),
      Map.entry("square meters", "Sqm"), Map.entry("sq m", "Sqm"), Map.entry("m2", "Sqm"),
      Map.entry("square feet", "Sqft"), Map.entry("square foot", "Sqft"), Map.entry("sq ft", "Sqft"),
      Map.entry("running meter", "Rm"), Map.entry("running metre", "Rm"),
      Map.entry("running meters", "Rm"), Map.entry("rmt", "Rm"), Map.entry("r mtr", "Rm"),
      Map.entry("linear meter", "Lm"), Map.entry("linear metre", "Lm"),
      Map.entry("meter", "m"), Map.entry("metre", "m"), Map.entry("meters", "m"),
      Map.entry("millimeter", "mm"), Map.entry("millimetre", "mm"),
      Map.entry("feet", "ft"), Map.entry("foot", "ft"), Map.entry("inch", "in"), Map.entry("inches", "in"),
      Map.entry("kilogram", "kg"), Map.entry("kilograms", "kg"), Map.entry("kilo", "kg"), Map.entry("kgs", "kg"),
      Map.entry("metric ton", "MT"), Map.entry("metric tonne", "MT"), Map.entry("metric tons", "MT"),
      Map.entry("ton", "MT"), Map.entry("tons", "MT"),
      Map.entry("tonne", "Tonne"), Map.entry("tonnes", "Tonne"),
      Map.entry("quintal", "Quintal"), Map.entry("quintals", "Quintal"),
      Map.entry("bag", "Bag"), Map.entry("bags", "Bag"),
      Map.entry("each", "Each"), Map.entry("number", "Nos"), Map.entry("numbers", "Nos"),
      Map.entry("no", "Nos"), Map.entry("pieces", "Nos"), Map.entry("piece", "Nos"),
      Map.entry("day", "Day"), Map.entry("days", "Day"),
      Map.entry("hour", "Hour"), Map.entry("hours", "Hour"), Map.entry("hr", "Hour"), Map.entry("hrs", "Hour"));

  private DprUnits() {}

  /**
   * Map a spoken/written unit to a canonical {@link #STANDARD_UNITS} code:
   * exact (case-insensitive) → synonym table → conservative fuzzy → raw passthrough (so the FE
   * fallback still shows an odd unit rather than blocking the user).
   */
  static String canonical(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return trimmed;
    // 1) exact, case-insensitive
    for (String u : STANDARD_UNITS) {
      if (u.equalsIgnoreCase(trimmed)) return u;
    }
    // 2) synonym table (normalized key)
    String syn = SYNONYMS.get(LabelMatcher.norm(trimmed));
    if (syn != null) return syn;
    // 3) conservative fuzzy against the canonical list
    DprLabelResolver.Resolved<String> r =
        DprLabelResolver.resolve(trimmed, STANDARD_UNITS, List::of);
    if (r.confident()) return r.best();
    // 4) unknown — pass through
    return trimmed;
  }
}
