package com.bipros.contract.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Discriminator for the polymorphic contract_attachments table.
 * The {@code entity_id} column is interpreted against this enum.
 */
public enum AttachmentEntityType {
    CONTRACT,
    MILESTONE,
    VARIATION_ORDER,
    PERFORMANCE_BOND;

    @JsonCreator
    public static AttachmentEntityType fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "CONTRACT" -> CONTRACT;
            case "MILESTONE" -> MILESTONE;
            case "VARIATION_ORDER", "VO" -> VARIATION_ORDER;
            case "PERFORMANCE_BOND", "BOND", "BG" -> PERFORMANCE_BOND;
            default -> throw new IllegalArgumentException(
                "Unknown AttachmentEntityType '" + value
                    + "' (valid: CONTRACT, MILESTONE, VARIATION_ORDER, PERFORMANCE_BOND)");
        };
    }
}
