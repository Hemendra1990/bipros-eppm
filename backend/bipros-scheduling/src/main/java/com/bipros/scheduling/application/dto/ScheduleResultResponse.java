package com.bipros.scheduling.application.dto;

import com.bipros.scheduling.domain.model.ScheduleResult;
import com.bipros.scheduling.domain.model.ScheduleStatus;
import com.bipros.scheduling.domain.model.SchedulingOption;

import java.time.Instant;
import java.time.LocalDate;
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
    ScheduleStatus status
) {

  public static ScheduleResultResponse from(ScheduleResult result) {
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
        result.getStatus()
    );
  }
}
