package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request to create or update a Manpower Category Master row. {@code parentId} null = top-level
 * Category; non-null = Sub-Category of that parent.
 */
public record ManpowerCategoryMasterRequest(
    @NotBlank(message = "Code is required")
    @Size(max = 50, message = "Code must be at most 50 characters")
    String code,

    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name must be at most 120 characters")
    String name,

    @Size(max = 500, message = "Description must be at most 500 characters")
    String description,

    UUID parentId,

    Integer sortOrder,

    Boolean active
) {}
