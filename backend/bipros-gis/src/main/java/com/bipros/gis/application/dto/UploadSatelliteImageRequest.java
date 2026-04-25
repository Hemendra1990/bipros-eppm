package com.bipros.gis.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UploadSatelliteImageRequest(
    @NotBlank(message = "Image name is required")
    String imageName,

    @NotNull(message = "Capture date is required")
    LocalDate captureDate,

    String description,

    String resolution,

    String boundingBoxGeoJson
) {}
