package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by ProjectService after a Project row update is committed. Listeners run
 * via @TransactionalEventListener(AFTER_COMMIT), so the updated project is already
 * visible to fresh transactions opened in handlers.
 */
public record ProjectUpdatedEvent(UUID projectId, String projectCode, String projectName) {
}
