package com.bipros.cost.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateRaBillItemRequest(
        @NotNull(message = "RA Bill ID is required")
        UUID raBillId,

        @NotBlank(message = "Item code is required")
        String itemCode,

        @NotBlank(message = "Description is required")
        String description,

        String unit,

        BigDecimal rate,

        Double previousQuantity,

        Double currentQuantity,

        Double cumulativeQuantity,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount
) {}
