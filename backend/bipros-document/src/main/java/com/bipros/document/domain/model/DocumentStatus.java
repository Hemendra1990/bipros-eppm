package com.bipros.document.domain.model;

public enum DocumentStatus {
    DRAFT,
    UNDER_REVIEW,
    APPROVED,
    SUPERSEDED,
    ARCHIVED,
    // IC-PMS M6 additions
    IFC,
    IFA,
    PUBLISHED,
    EXECUTED,
    VALID,
    OPEN,
    CLOSED
}
