package com.bipros.project.application.dto;

import java.time.Instant;
import java.util.UUID;

public record CorridorCodeResponse(
    UUID id,
    UUID projectId,
    String corridorPrefix,
    String zoneCode,
    String nodeCode,
    String generatedCode,
    Instant createdAt,
    Instant updatedAt
) {

    public static CorridorCodeResponse from(com.bipros.project.domain.model.CorridorCode corridorCode) {
        return new CorridorCodeResponse(
            corridorCode.getId(),
            corridorCode.getProjectId(),
            corridorCode.getCorridorPrefix(),
            corridorCode.getZoneCode(),
            corridorCode.getNodeCode(),
            corridorCode.getGeneratedCode(),
            corridorCode.getCreatedAt(),
            corridorCode.getUpdatedAt()
        );
    }
}
