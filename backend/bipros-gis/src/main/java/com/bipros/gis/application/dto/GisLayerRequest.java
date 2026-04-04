package com.bipros.gis.application.dto;

import com.bipros.gis.domain.model.GisLayerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GisLayerRequest(
    @NotBlank(message = "Layer name is required")
    String layerName,

    @NotNull(message = "Layer type is required")
    GisLayerType layerType,

    String description,

    Boolean isVisible,

    Double opacity,

    Integer sortOrder
) {}
