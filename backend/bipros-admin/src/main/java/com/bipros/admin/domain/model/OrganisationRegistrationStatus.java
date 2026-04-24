package com.bipros.admin.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Contractor/Organisation registration status per "PMS MasterData UI Screens Final" Screen 02.
 * Drives dashboard filters and KYC compliance views.
 */
public enum OrganisationRegistrationStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED,
    PENDING_KYC;

    @JsonCreator
    public static OrganisationRegistrationStatus fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "ACTIVE" -> ACTIVE;
            case "SUSPENDED" -> SUSPENDED;
            case "CLOSED", "INACTIVE" -> CLOSED;
            case "PENDING_KYC", "KYC_PENDING", "PENDING" -> PENDING_KYC;
            default -> throw new IllegalArgumentException(
                "Unknown OrganisationRegistrationStatus '" + value
                    + "' (valid: ACTIVE, SUSPENDED, CLOSED, PENDING_KYC)");
        };
    }
}
