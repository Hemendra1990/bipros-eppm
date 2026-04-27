package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProjectCategoryMasterRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    String name,

    String description,

    @NotNull(message = "Active is required")
    Boolean active,

    @NotNull(message = "Sort order is required")
    Integer sortOrder
) {}
