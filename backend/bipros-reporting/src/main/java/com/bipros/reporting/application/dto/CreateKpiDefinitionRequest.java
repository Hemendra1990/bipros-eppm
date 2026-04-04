package com.bipros.reporting.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateKpiDefinitionRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Code is required")
        String code,

        String formula,

        String unit,

        Double greenThreshold,

        Double amberThreshold,

        Double redThreshold,

        String moduleSource,

        @NotNull(message = "Is active flag is required")
        Boolean isActive
) {}
