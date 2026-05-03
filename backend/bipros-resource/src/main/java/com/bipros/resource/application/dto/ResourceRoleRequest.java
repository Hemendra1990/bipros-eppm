package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ResourceRoleRequest(
    @NotBlank(message = "Code is required")
    @Size(max = 50, message = "Code must be at most 50 characters")
    String code,

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    String name,

    @Size(max = 500, message = "Description must be at most 500 characters")
    String description,

    @NotNull(message = "resourceTypeId is required") UUID resourceTypeId,

    @Size(max = 50, message = "productivityUnit must be at most 50 characters")
    String productivityUnit,

    Integer sortOrder,

    Boolean active
) {}
