package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by BaselineService when a previously-active baseline is deactivated
 * (for example, when a new baseline supersedes it on a project+type). Listeners run
 * via @TransactionalEventListener(AFTER_COMMIT) and typically refresh the analytics
 * {@code dim_baseline} row so its {@code is_active} flag flips in real time.
 */
public record BaselineDeactivatedEvent(UUID projectId, UUID baselineId) {
}
