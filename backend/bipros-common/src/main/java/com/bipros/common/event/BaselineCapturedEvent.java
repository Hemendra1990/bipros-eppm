package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by BaselineService after a Baseline snapshot is committed. Listeners run
 * via @TransactionalEventListener(AFTER_COMMIT) and typically refresh the analytics
 * {@code dim_baseline} row so live dashboards see the new snapshot immediately.
 */
public record BaselineCapturedEvent(UUID projectId, UUID baselineId, String baselineName) {
}
