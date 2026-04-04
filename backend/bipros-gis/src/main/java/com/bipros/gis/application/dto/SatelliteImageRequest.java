package com.bipros.gis.application.dto;

import com.bipros.gis.domain.model.SatelliteImageSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record SatelliteImageRequest(
    @NotBlank(message = "Image name is required")
    String imageName,

    String description,

    @NotNull(message = "Capture date is required")
    LocalDate captureDate,

    @NotNull(message = "Source is required")
    SatelliteImageSource source,

    String resolution,

    String boundingBoxGeoJson,

    String filePath,

    @NotNull(message = "File size is required")
    Long fileSize,

    @NotBlank(message = "MIME type is required")
    String mimeType,

    Double northBound,

    Double southBound,

    Double eastBound,

    Double westBound,

    UUID layerId
) {}
