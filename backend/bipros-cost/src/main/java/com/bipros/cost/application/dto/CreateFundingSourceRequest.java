package com.bipros.cost.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateFundingSourceRequest(
        @NotBlank(message = "Name is required")
        String name,

        String description,

        String code,

        BigDecimal totalAmount,

        BigDecimal allocatedAmount,

        BigDecimal remainingAmount
) {
}
