package com.bipros.gis.application.dto;

import com.bipros.gis.domain.model.WbsPolygon;

import java.time.Instant;
import java.util.UUID;

public record WbsPolygonResponse(
    UUID id,
    UUID projectId,
    UUID wbsNodeId,
    UUID layerId,
    String wbsCode,
    String wbsName,
    String polygonGeoJson,
    Double centerLatitude,
    Double centerLongitude,
    Double areaInSqMeters,
    String fillColor,
    String strokeColor,
    Instant createdAt,
    String createdBy
) {
    public static WbsPolygonResponse from(WbsPolygon entity) {
        return new WbsPolygonResponse(
            entity.getId(),
            entity.getProjectId(),
            entity.getWbsNodeId(),
            entity.getLayerId(),
            entity.getWbsCode(),
            entity.getWbsName(),
            entity.getPolygonGeoJson(),
            entity.getCenterLatitude(),
            entity.getCenterLongitude(),
            entity.getAreaInSqMeters(),
            entity.getFillColor(),
            entity.getStrokeColor(),
            entity.getCreatedAt(),
            entity.getCreatedBy()
        );
    }
}
