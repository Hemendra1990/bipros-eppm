package com.bipros.cost.application.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCostAccountRequest(
        @NotBlank(message = "Name is required")
        String name,

        String description
) {
}
