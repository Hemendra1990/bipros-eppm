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
    String roleName,
    UUID effectiveRoleId,
    String effectiveRoleName,
    /** Productivity unit of the effective role (e.g. "Day", "Bag", "Nos") — used by the UI to
     * decide whether activity-level rollups can sum units (only when all assignments share it). */
    String unit,
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
    boolean staffed,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy) {

  /** Legacy constructor — names null. */
  public static ResourceAssignmentResponse from(ResourceAssignment assignment) {
    return from(assignment, null, null, null, null, null, null);
  }

  public static ResourceAssignmentResponse from(
      ResourceAssignment assignment,
      String resourceName,
      String activityName,
      String roleName,
      UUID effectiveRoleId,
      String effectiveRoleName,
      String unit) {
    return new ResourceAssignmentResponse(
        assignment.getId(),
        assignment.getActivityId(),
        activityName,
        assignment.getResourceId(),
        resourceName,
        assignment.getRoleId(),
        roleName,
        effectiveRoleId,
        effectiveRoleName,
        unit,
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
        assignment.getResourceId() != null,
        assignment.getCreatedAt(),
        assignment.getUpdatedAt(),
        assignment.getCreatedBy(),
        assignment.getUpdatedBy());
  }
}
