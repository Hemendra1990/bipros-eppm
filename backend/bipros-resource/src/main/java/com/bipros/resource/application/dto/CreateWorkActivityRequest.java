package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateWorkActivityRequest(
    @Size(max = 50, message = "code must be ≤ 50 characters")
    String code, // optional on create — service slugifies the name when blank

    @NotBlank(message = "name is required")
    @Size(max = 150)
    String name,

    @Size(max = 20)
    String defaultUnit,

    @Size(max = 50)
    String discipline,

    @Size(max = 500)
    String description,

    @PositiveOrZero
    Integer sortOrder,

    Boolean active
) {}
