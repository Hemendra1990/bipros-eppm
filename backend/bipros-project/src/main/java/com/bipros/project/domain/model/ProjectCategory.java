package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Road-construction project category per MoRTH taxonomy. The document
 * "PMS MasterData UI Screens Final" (Screen 01) calls this "Project Category"
 * and requires a linked MoRTH category code on the Project Master screen.
 */
public enum ProjectCategory {
    HIGHWAY,
    EXPRESSWAY,
    RURAL_ROAD,
    STATE_HIGHWAY,
    URBAN_ROAD,
    OTHER;

    @JsonCreator
    public static ProjectCategory fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "HIGHWAY", "NATIONAL_HIGHWAY", "NH" -> HIGHWAY;
            case "EXPRESSWAY", "EXP" -> EXPRESSWAY;
            case "RURAL_ROAD", "RURAL", "PMGSY" -> RURAL_ROAD;
            case "STATE_HIGHWAY", "SH" -> STATE_HIGHWAY;
            case "URBAN_ROAD", "URBAN" -> URBAN_ROAD;
            case "OTHER", "MISC" -> OTHER;
            default -> throw new IllegalArgumentException(
                "Unknown ProjectCategory '" + value
                    + "' (valid: HIGHWAY, EXPRESSWAY, RURAL_ROAD, STATE_HIGHWAY, URBAN_ROAD, OTHER)");
        };
    }
}
