package com.bipros.permit.domain.model;

/** Domain actions that drive transitions between {@link PermitStatus} values. */
public enum PermitAction {
    SUBMIT,
    APPROVE,
    REJECT,
    GAS_TEST_PASS,
    ISSUE,
    START,
    SUSPEND,
    RESUME,
    CLOSE,
    REVOKE,
    EXPIRE,
    AMEND
}
