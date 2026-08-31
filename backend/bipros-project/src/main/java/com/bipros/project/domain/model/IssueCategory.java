package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Reason / category of an issue logged on a DPR. Tolerant deserialiser accepts mixed case,
 * hyphens, and a few common aliases so seed data and API clients can be sloppy.
 */
public enum IssueCategory {
    SAFETY,
    QUALITY,
    MATERIAL_SHORTAGE,
    EQUIPMENT_BREAKDOWN,
    MANPOWER_SHORTAGE,
    WEATHER,
    DESIGN_CHANGE,
    LAND_ACCESS,
    UTILITY_CLASH,
    PERMIT_DELAY,
    SUBCONTRACTOR,
    ENVIRONMENTAL,
    OTHER;

    @JsonCreator
    public static IssueCategory fromString(String value) {
        if (value == null || value.isBlank()) return OTHER;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "SAFETY" -> SAFETY;
            case "QUALITY", "REWORK" -> QUALITY;
            case "MATERIAL_SHORTAGE", "MATERIAL", "MATERIAL_DELAY" -> MATERIAL_SHORTAGE;
            case "EQUIPMENT_BREAKDOWN", "EQUIPMENT", "PMV_BREAKDOWN" -> EQUIPMENT_BREAKDOWN;
            case "MANPOWER_SHORTAGE", "MANPOWER", "LABOR_SHORTAGE" -> MANPOWER_SHORTAGE;
            case "WEATHER", "RAIN", "STORM" -> WEATHER;
            case "DESIGN_CHANGE", "DESIGN", "DRAWING_CHANGE", "RFI" -> DESIGN_CHANGE;
            case "LAND_ACCESS", "ROW", "RIGHT_OF_WAY", "ACCESS" -> LAND_ACCESS;
            case "UTILITY_CLASH", "UTILITY", "CLASH" -> UTILITY_CLASH;
            case "PERMIT_DELAY", "PERMIT", "APPROVAL_DELAY" -> PERMIT_DELAY;
            case "SUBCONTRACTOR", "VENDOR" -> SUBCONTRACTOR;
            case "ENVIRONMENTAL", "ENVIRONMENT" -> ENVIRONMENTAL;
            case "OTHER", "MISC" -> OTHER;
            default -> throw new IllegalArgumentException(
                "Unknown IssueCategory '" + value + "'");
        };
    }
}
