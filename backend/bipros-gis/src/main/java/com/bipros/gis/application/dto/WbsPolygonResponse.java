package com.bipros.gis.application.dto;

import com.bipros.gis.domain.model.WbsPolygon;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

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
    /** GeoJsonWriter doesn't carry mutable state across write() calls. */
    private static final GeoJsonWriter GEOJSON = new GeoJsonWriter();
    static { GEOJSON.setEncodeCRS(false); }

    public static WbsPolygonResponse from(WbsPolygon entity) {
        String polygonJson = entity.getPolygon() != null ? GEOJSON.write(entity.getPolygon()) : null;
        return new WbsPolygonResponse(
            entity.getId(),
            entity.getProjectId(),
            entity.getWbsNodeId(),
            entity.getLayerId(),
            entity.getWbsCode(),
            entity.getWbsName(),
            polygonJson,
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
