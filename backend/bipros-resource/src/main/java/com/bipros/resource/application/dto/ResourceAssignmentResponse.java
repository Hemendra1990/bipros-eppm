package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceAssignment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ResourceAssignmentResponse(
    UUID id,
    UUID activityId,
    String activityName,
    UUID resourceId,
    String resourceName,
    UUID roleId,
    UUID projectId,
    Double plannedUnits,
    Double actualUnits,
    Double remainingUnits,
    Double atCompletionUnits,
    BigDecimal plannedCost,
    BigDecimal actualCost,
    BigDecimal remainingCost,
    BigDecimal atCompletionCost,
    String rateType,
    UUID resourceCurveId,
    LocalDate plannedStartDate,
    LocalDate plannedFinishDate,
    LocalDate actualStartDate,
    LocalDate actualFinishDate,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy) {

  /** Legacy constructor — names null; prefer {@link #from(ResourceAssignment, String, String)}. */
  public static ResourceAssignmentResponse from(ResourceAssignment assignment) {
    return from(assignment, null, null);
  }

  public static ResourceAssignmentResponse from(
      ResourceAssignment assignment, String resourceName, String activityName) {
    return new ResourceAssignmentResponse(
        assignment.getId(),
        assignment.getActivityId(),
        activityName,
        assignment.getResourceId(),
        resourceName,
        assignment.getRoleId(),
        assignment.getProjectId(),
        assignment.getPlannedUnits(),
        assignment.getActualUnits(),
        assignment.getRemainingUnits(),
        assignment.getAtCompletionUnits(),
        assignment.getPlannedCost(),
        assignment.getActualCost(),
        assignment.getRemainingCost(),
        assignment.getAtCompletionCost(),
        assignment.getRateType(),
        assignment.getResourceCurveId(),
        assignment.getPlannedStartDate(),
        assignment.getPlannedFinishDate(),
        assignment.getActualStartDate(),
        assignment.getActualFinishDate(),
        assignment.getCreatedAt(),
        assignment.getUpdatedAt(),
        assignment.getCreatedBy(),
        assignment.getUpdatedBy());
  }
}
