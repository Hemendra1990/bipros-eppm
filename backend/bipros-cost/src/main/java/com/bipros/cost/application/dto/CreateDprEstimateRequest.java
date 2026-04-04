package com.bipros.cost.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateDprEstimateRequest(
        @NotNull(message = "Project ID is required")
        UUID projectId,

        @NotNull(message = "WBS Node ID is required")
        UUID wbsNodeId,

        @NotNull(message = "Cost category is required")
        String costCategory,

        @NotNull(message = "Estimated amount is required")
        @Positive(message = "Estimated amount must be positive")
        BigDecimal estimatedAmount,

        BigDecimal revisedAmount,

        String remarks
) {}
