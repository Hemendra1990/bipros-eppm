package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by ActivityService after an Activity row update is committed. Listeners run
 * via @TransactionalEventListener(AFTER_COMMIT) and typically refresh the
 * analytics {@code dim_activity} row.
 */
public record ActivityUpdatedEvent(UUID projectId, UUID activityId, String activityCode, String activityName) {
}
