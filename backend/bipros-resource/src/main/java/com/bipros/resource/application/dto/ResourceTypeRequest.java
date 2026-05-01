package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResourceTypeRequest(
    @NotBlank(message = "Code is required")
    @Size(max = 30, message = "Code must be at most 30 characters")
    String code,

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    String name,

    @Size(max = 500, message = "Description must be at most 500 characters")
    String description,

    Integer sortOrder,

    Boolean active
) {}
