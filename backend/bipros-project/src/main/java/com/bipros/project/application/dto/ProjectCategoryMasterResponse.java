package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProjectCategoryMasterResponse(
    UUID id,
    String code,
    String name,
    String description,
    Boolean active,
    Integer sortOrder
) {}
