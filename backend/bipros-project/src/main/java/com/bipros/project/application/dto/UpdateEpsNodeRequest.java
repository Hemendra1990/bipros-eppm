package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record UpdateEpsNodeRequest(
    @NotBlank(message = "Name is required")
    String name,

    UUID obsId,

    Integer sortOrder
) {
}
