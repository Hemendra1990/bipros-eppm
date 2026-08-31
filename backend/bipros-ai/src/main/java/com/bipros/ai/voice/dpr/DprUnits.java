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

  // Client-approved 12-unit list (workbook "Web" sheet, DPR row 2, 01 Aug 2026).
  static final List<String> STANDARD_UNITS = List.of(
      "Cum", "Sqm", "Rm", "MT", "Nr", "LS", "Kg", "Hrs", "Bag", "Month", "Week", "Day");

  /**
   * Spoken/written variants → canonical code. Keys are stored already-normalized (lowercase,
   * single-spaced) and looked up via {@link LabelMatcher#norm(String)}.
   *
   * <p>Only dimension-identical relabels map onto the 12-list (metre→Rm, each→Nr, tonne→MT,
   * hour→Hrs). Magnitude-different retired units (Sqft, mm, ft, in, Quintal, L) keep their old
   * compact spelling — the value passes through and the FE renders it as "(legacy)"; we never
   * convert a quantity's unit to a different magnitude.
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
      Map.entry("linear meter", "Rm"), Map.entry("linear metre", "Rm"),
      Map.entry("meter", "Rm"), Map.entry("metre", "Rm"), Map.entry("meters", "Rm"),
      Map.entry("millimeter", "mm"), Map.entry("millimetre", "mm"),
      Map.entry("feet", "ft"), Map.entry("foot", "ft"), Map.entry("inch", "in"), Map.entry("inches", "in"),
      Map.entry("kilogram", "Kg"), Map.entry("kilograms", "Kg"), Map.entry("kilo", "Kg"), Map.entry("kgs", "Kg"),
      Map.entry("metric ton", "MT"), Map.entry("metric tons", "MT"),
      Map.entry("ton", "MT"), Map.entry("tons", "MT"),
      Map.entry("metric tonne", "MT"), Map.entry("tonne", "MT"), Map.entry("tonnes", "MT"),
      Map.entry("quintal", "Quintal"), Map.entry("quintals", "Quintal"),
      Map.entry("bag", "Bag"), Map.entry("bags", "Bag"),
      Map.entry("each", "Nr"), Map.entry("number", "Nr"), Map.entry("numbers", "Nr"),
      Map.entry("no", "Nr"), Map.entry("nos", "Nr"),
      Map.entry("pieces", "Nr"), Map.entry("piece", "Nr"),
      Map.entry("lump sum", "LS"), Map.entry("lumpsum", "LS"),
      Map.entry("month", "Month"), Map.entry("months", "Month"),
      Map.entry("week", "Week"), Map.entry("weeks", "Week"),
      Map.entry("day", "Day"), Map.entry("days", "Day"),
      Map.entry("hour", "Hrs"), Map.entry("hours", "Hrs"), Map.entry("hr", "Hrs"));

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
