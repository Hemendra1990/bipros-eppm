package com.bipros.risk.domain.model;

public enum RiskStatus {
    IDENTIFIED,
    ANALYZING,
    MITIGATING,
    RESOLVED,
    CLOSED,
    ACCEPTED,
    // IC-PMS M7 — Excel spec open-state sub-variants + realisation states
    OPEN_ESCALATED,
    OPEN_UNDER_ACTIVE_MANAGEMENT,
    OPEN_BEING_MANAGED,
    OPEN_MONITOR,
    OPEN_WATCH,
    OPEN_TARGET,
    OPEN_ASI_REVIEW,
    REALISED_PARTIALLY
}
