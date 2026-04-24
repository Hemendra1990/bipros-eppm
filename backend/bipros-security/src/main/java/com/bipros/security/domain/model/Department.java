package com.bipros.security.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Functional department per PMS MasterData Screen 07 (Personnel Master). Drives role-based
 * access rights in addition to reporting breakdowns.
 */
public enum Department {
    CIVIL,
    QUALITY,
    SURVEY,
    PLANT,
    HSE,
    STORES,
    ADMIN,
    FINANCE,
    OTHER;

    @JsonCreator
    public static Department fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "CIVIL", "CIVIL_ENGINEERING" -> CIVIL;
            case "QUALITY", "QA_QC", "QC" -> QUALITY;
            case "SURVEY", "SURVEYING" -> SURVEY;
            case "PLANT", "MACHINERY" -> PLANT;
            case "HSE", "SAFETY" -> HSE;
            case "STORES", "STORE" -> STORES;
            case "ADMIN", "ADMINISTRATION" -> ADMIN;
            case "FINANCE", "ACCOUNTS" -> FINANCE;
            case "OTHER", "MISC" -> OTHER;
            default -> throw new IllegalArgumentException(
                "Unknown Department '" + value
                    + "' (valid: CIVIL, QUALITY, SURVEY, PLANT, HSE, STORES, ADMIN, FINANCE, OTHER)");
        };
    }
}
