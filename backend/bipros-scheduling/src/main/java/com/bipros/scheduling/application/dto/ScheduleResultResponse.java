package com.bipros.scheduling.application.dto;

import com.bipros.scheduling.domain.model.ScheduleResult;
import com.bipros.scheduling.domain.model.ScheduleStatus;
import com.bipros.scheduling.domain.model.SchedulingOption;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ScheduleResultResponse(
    UUID id,
    UUID projectId,
    LocalDate dataDate,
    LocalDate projectStartDate,
    LocalDate projectFinishDate,
    Double criticalPathLength,
    int totalActivities,
    int criticalActivities,
    SchedulingOption schedulingOption,
    Instant calculatedAt,
    Double durationSeconds,
    ScheduleStatus status,
    List<String> warnings,
    /** Counts of NOT_STARTED activities. Null on cached/historical results that pre-date Phase 1.6. */
    Integer notStartedActivities,
    /** Counts of IN_PROGRESS activities. Null on cached/historical results. */
    Integer inProgressActivities,
    /** Counts of COMPLETED activities. Null on cached/historical results. */
    Integer completedActivities
) {

  public static ScheduleResultResponse from(ScheduleResult result) {
    return from(result, List.of(), null, null, null);
  }

  public static ScheduleResultResponse from(ScheduleResult result, List<String> warnings) {
    return from(result, warnings, null, null, null);
  }

  public static ScheduleResultResponse from(
      ScheduleResult result,
      List<String> warnings,
      Integer notStarted,
      Integer inProgress,
      Integer completed
  ) {
    return new ScheduleResultResponse(
        result.getId(),
        result.getProjectId(),
        result.getDataDate(),
        result.getProjectStartDate(),
        result.getProjectFinishDate(),
        result.getCriticalPathLength(),
        result.getTotalActivities(),
        result.getCriticalActivities(),
        result.getSchedulingOption(),
        result.getCalculatedAt(),
        result.getDurationSeconds(),
        result.getStatus(),
        warnings != null ? warnings : List.of(),
        notStarted,
        inProgress,
        completed
    );
  }
}
