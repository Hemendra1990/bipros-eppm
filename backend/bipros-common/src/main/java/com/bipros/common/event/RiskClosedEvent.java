package com.bipros.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a risk transitions to a terminal state (CLOSED or RESOLVED) or is hard-deleted.
 * Triggers a final {@code fact_risk_snapshot_daily} row so downstream dashboards see the closure.
 */
public record RiskClosedEvent(
        UUID projectId,
        UUID riskId,
        Instant closedAt,
        UUID closedBy
) {
}
