package com.bipros.scheduling.domain.algorithm;

import java.util.UUID;

public record SchedulableRelationship(
    UUID predecessorId,
    UUID successorId,
    String type,
    double lag
) {
}
