package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by SchedulingService after a {@code ScheduleResult} row is committed
 * (i.e., after a CPM/leveling run completes). Listeners run via
 * @TransactionalEventListener(AFTER_COMMIT) and typically refresh the analytics
 * {@code dim_schedule_run} row. {@code runType} is the {@link
 * com.bipros.scheduling.domain.model.SchedulingOption} name (RETAINED_LOGIC,
 * PROGRESS_OVERRIDE, …) for traceability.
 */
public record ScheduleRunRecordedEvent(UUID projectId, UUID scheduleRunId, String runType) {
}
