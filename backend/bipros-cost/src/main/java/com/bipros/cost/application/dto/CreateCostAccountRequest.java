package com.bipros.cost.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateCostAccountRequest(
        @NotBlank(message = "Code is required")
        String code,

        @NotBlank(message = "Name is required")
        String name,

        String description,

        UUID parentId,

        Integer sortOrder
) {
}
