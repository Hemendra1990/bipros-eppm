package com.bipros.permit.application.dto;

import com.bipros.permit.domain.model.LifecycleEventType;

import java.time.Instant;
import java.util.UUID;

public record LifecycleEventDto(
        UUID id,
        LifecycleEventType eventType,
        String payloadJson,
        Instant occurredAt,
        UUID actorUserId
) {}
