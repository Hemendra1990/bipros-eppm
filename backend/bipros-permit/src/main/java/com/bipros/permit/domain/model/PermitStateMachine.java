package com.bipros.permit.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Permit lifecycle transition rules. Service code calls
 * {@link #assertCanTransition(PermitStatus, PermitAction)} before mutating;
 * illegal transitions throw at the boundary so the global handler can surface a clean 422.
 */
public final class PermitStateMachine {

    private static final Map<PermitStatus, Set<PermitAction>> ALLOWED_ACTIONS = new EnumMap<>(PermitStatus.class);

    static {
        ALLOWED_ACTIONS.put(PermitStatus.DRAFT, EnumSet.of(PermitAction.SUBMIT, PermitAction.AMEND));
        ALLOWED_ACTIONS.put(PermitStatus.PENDING_SITE_ENGINEER, EnumSet.of(PermitAction.APPROVE, PermitAction.REJECT));
        ALLOWED_ACTIONS.put(PermitStatus.PENDING_HSE, EnumSet.of(PermitAction.APPROVE, PermitAction.REJECT, PermitAction.GAS_TEST_PASS));
        ALLOWED_ACTIONS.put(PermitStatus.AWAITING_GAS_TEST, EnumSet.of(PermitAction.GAS_TEST_PASS, PermitAction.REJECT));
        ALLOWED_ACTIONS.put(PermitStatus.PENDING_PM, EnumSet.of(PermitAction.APPROVE, PermitAction.REJECT));
        ALLOWED_ACTIONS.put(PermitStatus.APPROVED, EnumSet.of(PermitAction.ISSUE, PermitAction.REVOKE));
        ALLOWED_ACTIONS.put(PermitStatus.ISSUED, EnumSet.of(PermitAction.START, PermitAction.SUSPEND, PermitAction.REVOKE, PermitAction.CLOSE, PermitAction.EXPIRE, PermitAction.AMEND));
        ALLOWED_ACTIONS.put(PermitStatus.IN_PROGRESS, EnumSet.of(PermitAction.SUSPEND, PermitAction.REVOKE, PermitAction.CLOSE, PermitAction.EXPIRE, PermitAction.AMEND));
        ALLOWED_ACTIONS.put(PermitStatus.SUSPENDED, EnumSet.of(PermitAction.RESUME, PermitAction.REVOKE, PermitAction.CLOSE));
        ALLOWED_ACTIONS.put(PermitStatus.CLOSED, EnumSet.noneOf(PermitAction.class));
        ALLOWED_ACTIONS.put(PermitStatus.REJECTED, EnumSet.of(PermitAction.AMEND));
        ALLOWED_ACTIONS.put(PermitStatus.EXPIRED, EnumSet.of(PermitAction.AMEND));
        ALLOWED_ACTIONS.put(PermitStatus.REVOKED, EnumSet.noneOf(PermitAction.class));
    }

    private PermitStateMachine() {
    }

    public static boolean canTransition(PermitStatus from, PermitAction action) {
        Set<PermitAction> allowed = ALLOWED_ACTIONS.get(from);
        return allowed != null && allowed.contains(action);
    }

    public static void assertCanTransition(PermitStatus from, PermitAction action) {
        if (!canTransition(from, action)) {
            throw new IllegalStateException("Permit in status " + from + " cannot perform action " + action);
        }
    }

    /** Whether this status is a terminal state (no further transitions besides AMEND). */
    public static boolean isTerminal(PermitStatus status) {
        return status == PermitStatus.CLOSED
                || status == PermitStatus.REVOKED
                || status == PermitStatus.REJECTED
                || status == PermitStatus.EXPIRED;
    }
}
