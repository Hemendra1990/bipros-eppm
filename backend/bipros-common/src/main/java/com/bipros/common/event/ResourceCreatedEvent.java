package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by ResourceService after a Resource row is committed. Resources are
 * project-agnostic (a single resource pool spans all projects), so no projectId
 * is carried. Listeners run via @TransactionalEventListener(AFTER_COMMIT).
 */
public record ResourceCreatedEvent(UUID resourceId, String resourceCode, String resourceName) {
}
