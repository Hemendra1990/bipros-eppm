package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by WbsService after a WbsNode row is committed. Listeners run
 * via @TransactionalEventListener(AFTER_COMMIT) and typically refresh the
 * analytics {@code dim_wbs} row.
 */
public record WbsCreatedEvent(UUID projectId, UUID wbsId, String wbsCode, String wbsName) {
}
