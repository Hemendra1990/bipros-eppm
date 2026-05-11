package com.bipros.common.event;

import java.util.List;
import java.util.UUID;

/**
 * Published for bulk activity mutations (P6 import, Excel upload, mass status update, etc.)
 * so analytics listeners can issue a single bulk INSERT into {@code dim_activity} rather
 * than reacting to thousands of per-row events. Listeners run via
 * @TransactionalEventListener(AFTER_COMMIT).
 */
public record ActivitiesBulkUpdatedEvent(UUID projectId, List<UUID> activityIds) {
  public ActivitiesBulkUpdatedEvent {
    if (activityIds == null) {
      activityIds = List.of();
    }
  }
}
