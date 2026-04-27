package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.Industry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateRiskCategoryMasterRequest(
    @NotBlank(message = "Code is required")
    @Size(max = 60, message = "Code must not exceed 60 characters")
    String code,

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    String name,

    String description,

    @NotNull(message = "Type id is required")
    UUID typeId,

    @NotNull(message = "Industry is required")
    Industry industry,

    @NotNull(message = "Active is required")
    Boolean active,

    @NotNull(message = "Sort order is required")
    Integer sortOrder
) {}
