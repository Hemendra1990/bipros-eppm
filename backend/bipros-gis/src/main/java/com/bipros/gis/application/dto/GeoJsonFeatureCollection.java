package com.bipros.gis.application.dto;

import java.util.List;

public record GeoJsonFeatureCollection(
    String type,
    List<GeoJsonFeature> features
) {
    public GeoJsonFeatureCollection {
        if (type == null || !type.equals("FeatureCollection")) {
            throw new IllegalArgumentException("Type must be 'FeatureCollection'");
        }
    }

    public static GeoJsonFeatureCollection create(List<GeoJsonFeature> features) {
        return new GeoJsonFeatureCollection("FeatureCollection", features);
    }
}
