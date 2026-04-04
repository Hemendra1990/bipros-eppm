package com.bipros.gis.application.dto;

import com.bipros.gis.domain.model.GisLayer;
import com.bipros.gis.domain.model.GisLayerType;

import java.time.Instant;
import java.util.UUID;

public record GisLayerResponse(
    UUID id,
    UUID projectId,
    String layerName,
    GisLayerType layerType,
    String description,
    Boolean isVisible,
    Double opacity,
    Integer sortOrder,
    Instant createdAt,
    String createdBy
) {
    public static GisLayerResponse from(GisLayer entity) {
        return new GisLayerResponse(
            entity.getId(),
            entity.getProjectId(),
            entity.getLayerName(),
            entity.getLayerType(),
            entity.getDescription(),
            entity.getIsVisible(),
            entity.getOpacity(),
            entity.getSortOrder(),
            entity.getCreatedAt(),
            entity.getCreatedBy()
        );
    }
}
