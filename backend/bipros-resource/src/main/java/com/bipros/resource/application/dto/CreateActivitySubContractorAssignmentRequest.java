package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotNull;

public record CreateActivitySubContractorAssignmentRequest(
    @NotNull(message = "activityId is required")
    String activityId,

    @NotNull(message = "subContractorMasterId is required")
    String subContractorMasterId
) {}
