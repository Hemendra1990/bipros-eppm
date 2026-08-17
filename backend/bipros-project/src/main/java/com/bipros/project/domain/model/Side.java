package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Carriageway side / corridor element for a chainage band on a road/highway DPR row.
 * Extended list from the client workbook (01 Aug 2026), first image under "SIDE".
 *
 * <p>Every constant name must stay ≤ 10 characters: the {@code side} column is VARCHAR(10)
 * in both dev (Hibernate-created) and prod (Liquibase changeset 072), and neither path
 * widens an existing column. Hence {@code CDROAD_RHS}, not {@code CD_ROAD_RHS}.</p>
 */
public enum Side {
    LHS,
    RHS,
    CENTER,
    MEDIAN_LHS,
    MEDIAN_RHS,
    MCW_LHS,
    MCW_RHS,
    CDROAD_LHS,
    CDROAD_RHS;

    @JsonCreator
    public static Side fromString(String value) {
        if (value == null || value.isBlank()) return null;
        // Collapse runs of whitespace / hyphen / en–em dash to a single underscore so the
        // client's own spellings ("MCW -RHS", "CD Road – RHS") all normalize cleanly.
        String n = value.trim().toUpperCase().replaceAll("[\\s\\-\\u2013\\u2014]+", "_");
        return switch (n) {
            case "LHS", "LEFT" -> LHS;
            case "RHS", "RIGHT" -> RHS;
            case "CENTER", "CENTRE", "MEDIAN", "BOTH" -> CENTER;
            case "MEDIAN_LHS", "LHS_MEDIAN" -> MEDIAN_LHS;
            case "MEDIAN_RHS", "RHS_MEDIAN" -> MEDIAN_RHS;
            case "MCW_LHS" -> MCW_LHS;
            case "MCW_RHS" -> MCW_RHS;
            case "CDROAD_LHS", "CD_ROAD_LHS" -> CDROAD_LHS;
            case "CDROAD_RHS", "CD_ROAD_RHS" -> CDROAD_RHS;
            default -> throw new IllegalArgumentException(
                "Unknown Side '" + value + "' (valid: LHS, RHS, CENTER, MEDIAN_LHS, MEDIAN_RHS, "
                    + "MCW_LHS, MCW_RHS, CDROAD_LHS, CDROAD_RHS)");
        };
    }
}
