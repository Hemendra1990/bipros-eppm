package com.bipros.baseline.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ScheduleComparisonResponse(
    UUID activityId,
    String activityName,
    LocalDate currentStart,
    LocalDate baselineStart,
    Long startVarianceDays,
    LocalDate currentFinish,
    LocalDate baselineFinish,
    Long finishVarianceDays,
    ComparisonStatus status) {

  public enum ComparisonStatus {
    ADDED,
    DELETED,
    CHANGED,
    UNCHANGED
  }
}
