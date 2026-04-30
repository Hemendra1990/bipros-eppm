package com.bipros.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Coarse-grained event published whenever a permit lifecycle row is recorded.
 * The {@code eventType} string carries the {@code LifecycleEventType} enum name
 * (kept as a String to avoid bipros-common depending on bipros-permit).
 */
public record PermitLifecycleRecordedEvent(
        UUID projectId,
        UUID permitId,
        UUID permitTypeTemplateId,
        String eventType,
        Instant occurredAt,
        UUID actorUserId,
        String riskLevel,
        String permitStatus,
        String payloadJson
) {
}
