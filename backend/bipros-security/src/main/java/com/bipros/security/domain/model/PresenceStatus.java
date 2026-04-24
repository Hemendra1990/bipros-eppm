package com.bipros.security.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Site presence status per PMS MasterData Screen 07.
 */
public enum PresenceStatus {
    ON_SITE,
    ON_LEAVE,
    TRANSFERRED,
    RELEASED;

    @JsonCreator
    public static PresenceStatus fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "ON_SITE", "ONSITE", "AT_SITE" -> ON_SITE;
            case "ON_LEAVE", "LEAVE" -> ON_LEAVE;
            case "TRANSFERRED", "TRANSFER" -> TRANSFERRED;
            case "RELEASED", "RELEASE", "EXITED" -> RELEASED;
            default -> throw new IllegalArgumentException(
                "Unknown PresenceStatus '" + value
                    + "' (valid: ON_SITE, ON_LEAVE, TRANSFERRED, RELEASED)");
        };
    }
}
