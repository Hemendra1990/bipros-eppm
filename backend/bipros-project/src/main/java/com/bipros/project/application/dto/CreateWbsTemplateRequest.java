package com.bipros.project.application.dto;

import com.bipros.project.domain.model.AssetClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateWbsTemplateRequest(
    @NotBlank(message = "Code is required")
    String code,

    @NotBlank(message = "Name is required")
    String name,

    @NotNull(message = "Asset class is required")
    AssetClass assetClass,

    String description,

    @NotBlank(message = "Default structure JSON is required")
    String defaultStructure,

    Boolean isActive
) {
}
