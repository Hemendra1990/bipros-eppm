package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Update payload. {@code code} and {@code baseCategory} are accepted from clients but the service
 * rejects mutations to either field on system-default rows.
 */
public record UpdateResourceTypeDefRequest(
    @NotBlank(message = "Code is required")
    @Size(max = 50, message = "Code must be at most 50 characters")
    String code,

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    String name,

    ResourceType baseCategory,

    @Size(max = 10, message = "Code prefix must be at most 10 characters")
    String codePrefix,

    Integer sortOrder,

    Boolean active
) {}
