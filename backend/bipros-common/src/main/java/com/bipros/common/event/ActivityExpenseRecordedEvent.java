package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by {@code CostService} or {@code ActivityExpenseService} after an expense is saved.
 * Triggers analytics ETL into {@code fact_cost_daily}.
 */
public record ActivityExpenseRecordedEvent(
    UUID projectId,
    UUID expenseId,
    UUID activityId
) {
}
