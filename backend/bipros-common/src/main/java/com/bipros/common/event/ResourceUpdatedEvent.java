package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by ResourceService after a Resource row update is committed. Resources are
 * project-agnostic (a single resource pool spans all projects), so no projectId is
 * carried. Listeners run via @TransactionalEventListener(AFTER_COMMIT).
 */
public record ResourceUpdatedEvent(UUID resourceId, String resourceCode, String resourceName) {
}
