package com.bipros.common.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published by {@code DailyActivityResourceOutputService} after the units rollup commits.
 * Listeners run via {@link org.springframework.transaction.event.TransactionalEventListener}
 * AFTER_COMMIT so {@code resource_assignments.actual_units} is already up to date when handlers fire.
 *
 * <p>The cost rollup ({@code actual_cost}, {@code remaining_cost}, {@code at_completion_cost})
 * lives in {@code bipros-resource} because it owns {@code ResourceRate}; this event is the
 * decoupling seam.
 */
public record DailyOutputChangedEvent(UUID projectId, UUID activityId, UUID resourceId,
                                      LocalDate outputDate, BigDecimal qtyExecuted) {
}
