package com.bipros.ai.agent.domain;

/** Lifecycle status of an {@link AgentFinding} in the shared memory store. */
public enum FindingStatus {
    /** Current, live finding. At most one ACTIVE row per fingerprint (partial unique index). */
    ACTIVE,
    /** Replaced by a newer ACTIVE finding with the same fingerprint but changed content. */
    SUPERSEDED,
    /** Explicitly resolved by a user. */
    RESOLVED_BY_USER,
    /** Passed its {@code validUntil} TTL and swept out. */
    EXPIRED
}
