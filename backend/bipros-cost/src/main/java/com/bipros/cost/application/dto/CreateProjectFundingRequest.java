package com.bipros.cost.application.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProjectFundingRequest(
        @NotNull(message = "Project ID is required")
        UUID projectId,

        @NotNull(message = "Funding Source ID is required")
        UUID fundingSourceId,

        UUID wbsNodeId,

        BigDecimal allocatedAmount
) {
}
