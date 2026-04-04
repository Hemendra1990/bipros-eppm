package com.bipros.gis.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WbsPolygonRequest(
    @NotNull(message = "WBS Node ID is required")
    UUID wbsNodeId,

    @NotNull(message = "Layer ID is required")
    UUID layerId,

    @NotBlank(message = "WBS code is required")
    String wbsCode,

    @NotBlank(message = "WBS name is required")
    String wbsName,

    @NotNull(message = "Polygon GeoJSON is required")
    String polygonGeoJson,

    @NotNull(message = "Center latitude is required")
    Double centerLatitude,

    @NotNull(message = "Center longitude is required")
    Double centerLongitude,

    Double areaInSqMeters,

    String fillColor,

    String strokeColor
) {}
