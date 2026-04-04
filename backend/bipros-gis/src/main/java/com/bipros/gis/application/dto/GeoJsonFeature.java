package com.bipros.gis.application.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public record GeoJsonFeature(
    String type,
    Map<String, Object> properties,
    JsonNode geometry
) {
    public GeoJsonFeature {
        if (type == null || !type.equals("Feature")) {
            throw new IllegalArgumentException("Type must be 'Feature'");
        }
    }

    public static GeoJsonFeature create(Map<String, Object> properties, JsonNode geometry) {
        return new GeoJsonFeature("Feature", properties, geometry);
    }
}
