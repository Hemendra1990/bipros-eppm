package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by CostService after a CostAccount row update is committed. {@code projectId}
 * may be null because cost accounts are organisation-wide today; carried as a hint
 * for future project-scoped accounts. Listeners run via
 * @TransactionalEventListener(AFTER_COMMIT).
 */
public record CostAccountUpdatedEvent(UUID projectId, UUID costAccountId, String code, String name) {
}
