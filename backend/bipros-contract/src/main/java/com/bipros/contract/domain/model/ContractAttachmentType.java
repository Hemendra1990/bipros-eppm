package com.bipros.contract.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Document classification used by Primavera Contract Management / Unifier
 * to tag uploads against contracts and their sub-entities.
 */
public enum ContractAttachmentType {
    LOA,
    AGREEMENT,
    BOQ,
    DRAWING,
    BG_SCAN,
    MOM,
    MEASUREMENT_BOOK,
    TEST_REPORT,
    CERTIFICATE,
    PHOTO,
    OTHER;

    @JsonCreator
    public static ContractAttachmentType fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "LOA", "LETTER_OF_AWARD" -> LOA;
            case "AGREEMENT", "CONTRACT_AGREEMENT" -> AGREEMENT;
            case "BOQ", "BILL_OF_QUANTITIES" -> BOQ;
            case "DRAWING" -> DRAWING;
            case "BG_SCAN", "BANK_GUARANTEE", "BG" -> BG_SCAN;
            case "MOM", "MINUTES_OF_MEETING" -> MOM;
            case "MEASUREMENT_BOOK", "MB" -> MEASUREMENT_BOOK;
            case "TEST_REPORT" -> TEST_REPORT;
            case "CERTIFICATE" -> CERTIFICATE;
            case "PHOTO", "PHOTOGRAPH", "IMAGE" -> PHOTO;
            case "OTHER" -> OTHER;
            default -> throw new IllegalArgumentException(
                "Unknown ContractAttachmentType '" + value
                    + "' (valid: LOA, AGREEMENT, BOQ, DRAWING, BG_SCAN, MOM, MEASUREMENT_BOOK, TEST_REPORT, CERTIFICATE, PHOTO, OTHER)");
        };
    }
}
