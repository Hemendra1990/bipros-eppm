package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateActivitySubContractorAssignmentRequest(
    @NotNull(message = "activityId is required")
    String activityId,

    @NotNull(message = "subContractorMasterId is required")
    String subContractorMasterId,

    @NotNull(message = "units is required")
    BigDecimal units
) {}
