package com.bipros.scheduling.application.dto;

import com.bipros.scheduling.domain.model.ScheduleActivityResult;

import java.time.LocalDate;
import java.util.UUID;

public record ScheduleActivityResultResponse(
    UUID id,
    UUID scheduleResultId,
    UUID activityId,
    LocalDate earlyStart,
    LocalDate earlyFinish,
    LocalDate lateStart,
    LocalDate lateFinish,
    Double totalFloat,
    Double freeFloat,
    Boolean isCritical,
    Double remainingDuration
) {

  public static ScheduleActivityResultResponse from(ScheduleActivityResult result) {
    return new ScheduleActivityResultResponse(
        result.getId(),
        result.getScheduleResultId(),
        result.getActivityId(),
        result.getEarlyStart(),
        result.getEarlyFinish(),
        result.getLateStart(),
        result.getLateFinish(),
        result.getTotalFloat(),
        result.getFreeFloat(),
        result.getIsCritical(),
        result.getRemainingDuration()
    );
  }
}
