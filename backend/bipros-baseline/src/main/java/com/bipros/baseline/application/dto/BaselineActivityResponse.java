package com.bipros.baseline.application.dto;

import com.bipros.baseline.domain.BaselineActivity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BaselineActivityResponse(
    UUID id,
    UUID baselineId,
    UUID activityId,
    LocalDate earlyStart,
    LocalDate earlyFinish,
    LocalDate lateStart,
    LocalDate lateFinish,
    Double originalDuration,
    Double remainingDuration,
    Double totalFloat,
    Double freeFloat,
    BigDecimal plannedCost,
    BigDecimal actualCost,
    Double percentComplete,
    Instant createdAt,
    Instant updatedAt) {

  public static BaselineActivityResponse from(BaselineActivity activity) {
    return new BaselineActivityResponse(
        activity.getId(),
        activity.getBaselineId(),
        activity.getActivityId(),
        activity.getEarlyStart(),
        activity.getEarlyFinish(),
        activity.getLateStart(),
        activity.getLateFinish(),
        activity.getOriginalDuration(),
        activity.getRemainingDuration(),
        activity.getTotalFloat(),
        activity.getFreeFloat(),
        activity.getPlannedCost(),
        activity.getActualCost(),
        activity.getPercentComplete(),
        activity.getCreatedAt(),
        activity.getUpdatedAt());
  }
}
