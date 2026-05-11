package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by WbsService after a WbsNode row update is committed. Listeners run
 * via @TransactionalEventListener(AFTER_COMMIT) and typically refresh the
 * analytics {@code dim_wbs} row.
 */
public record WbsUpdatedEvent(UUID projectId, UUID wbsId, String wbsCode, String wbsName) {
}
