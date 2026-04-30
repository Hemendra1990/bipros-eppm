package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by {@code ResourceAssignmentCostRollupListener} after the cost rollup commits.
 * Listeners run via {@link org.springframework.transaction.event.TransactionalEventListener}
 * AFTER_COMMIT so resource_assignments actuals/sums are already up to date.
 *
 * <p>The sums travel inside the payload — bipros-activity does not query bipros-resource
 * (preserving the no-cross-module-deps rule).
 */
public record ResourceAssignmentActualsRolledUpEvent(
    UUID projectId, UUID activityId,
    Double plannedUnitsSum, Double actualUnitsSum) {
}
