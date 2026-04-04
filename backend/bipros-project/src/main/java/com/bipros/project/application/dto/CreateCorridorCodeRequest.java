package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateCorridorCodeRequest(
    @NotNull(message = "Project ID is required")
    UUID projectId,

    @NotBlank(message = "Corridor prefix is required")
    String corridorPrefix,

    @NotBlank(message = "Zone code is required")
    String zoneCode,

    @NotBlank(message = "Node code is required")
    String nodeCode
) {
}
