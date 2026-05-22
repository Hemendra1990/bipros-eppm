package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateActivitySubContractorAssignmentRequest(
    @NotNull(message = "activityId is required")
    String activityId,

    @NotNull(message = "subContractorMasterId is required")
    String subContractorMasterId,

    @NotNull(message = "workActivityId is required")
    String workActivityId,

    @NotNull(message = "plannedUnits is required")
    @Positive(message = "plannedUnits must be positive")
    BigDecimal plannedUnits
) {}
