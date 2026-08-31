package com.bipros.project.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Lightweight approval state for a DPR row. Workflow transitions are not enforced server-side yet. */
public enum DprApprovalStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED;

    @JsonCreator
    public static DprApprovalStatus fromString(String value) {
        if (value == null || value.isBlank()) return DRAFT;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "DRAFT", "PENDING" -> DRAFT;
            case "SUBMITTED", "AWAITING_APPROVAL" -> SUBMITTED;
            case "APPROVED", "ACCEPTED" -> APPROVED;
            case "REJECTED", "DECLINED" -> REJECTED;
            default -> throw new IllegalArgumentException(
                "Unknown DprApprovalStatus '" + value + "' (valid: DRAFT, SUBMITTED, APPROVED, REJECTED)");
        };
    }
}
