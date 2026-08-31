package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Sub-classification of a safety/environmental {@link DprIssue} used only by the HSE statistics
 * tab. Nullable on the entity: existing SAFETY issues with no type stay valid and are not counted
 * toward any incident tally. Tolerant deserialiser mirrors {@link IssueCategory} so API clients and
 * seed data can be sloppy with case / hyphens / spaces.
 */
public enum HseIncidentType {
    LTI,
    MTC,
    NEAR_MISS,
    FATALITY,
    PROPERTY_DAMAGE;

    @JsonCreator
    public static HseIncidentType fromString(String value) {
        if (value == null || value.isBlank()) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "LTI", "LOST_TIME_INJURY" -> LTI;
            case "MTC", "MEDICAL_TREATMENT_CASE" -> MTC;
            case "NEAR_MISS", "NMC", "NEAR_MISS_CASE" -> NEAR_MISS;
            case "FATALITY", "FATAL" -> FATALITY;
            case "PROPERTY_DAMAGE", "ASSET_DAMAGE", "PROPERTY", "ASSET" -> PROPERTY_DAMAGE;
            default -> throw new IllegalArgumentException("Unknown HseIncidentType '" + value + "'");
        };
    }
}
