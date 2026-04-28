package com.bipros.permit.domain.model;

/** Lifecycle states of a Permit-to-Work. Transitions are gated by {@link PermitStateMachine}. */
public enum PermitStatus {
    DRAFT,
    PENDING_SITE_ENGINEER,
    PENDING_HSE,
    AWAITING_GAS_TEST,
    PENDING_PM,
    APPROVED,
    ISSUED,
    IN_PROGRESS,
    SUSPENDED,
    CLOSED,
    REJECTED,
    EXPIRED,
    REVOKED
}
