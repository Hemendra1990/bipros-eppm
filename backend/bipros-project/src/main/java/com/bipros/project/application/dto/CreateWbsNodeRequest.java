package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateWbsNodeRequest(
    @NotBlank(message = "Code is required")
    String code,

    @NotBlank(message = "Name is required")
    String name,

    UUID parentId,

    @NotNull(message = "Project ID is required")
    UUID projectId,

    UUID obsNodeId
) {
}
