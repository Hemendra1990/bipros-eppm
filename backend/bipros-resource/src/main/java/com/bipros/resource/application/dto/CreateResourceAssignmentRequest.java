package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateResourceAssignmentRequest(
    @NotNull(message = "Activity ID is required") UUID activityId,
    @NotNull(message = "Resource ID is required") UUID resourceId,
    UUID roleId,
    @NotNull(message = "Project ID is required") UUID projectId,
    Double plannedUnits,
    String rateType,
    UUID resourceCurveId,
    LocalDate plannedStartDate,
    LocalDate plannedFinishDate) {}
