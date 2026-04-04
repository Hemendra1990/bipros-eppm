package com.bipros.scheduling.domain.algorithm;

import java.time.LocalDate;
import java.util.UUID;

public record SchedulableActivity(
    UUID id,
    double originalDuration,
    double remainingDuration,
    UUID calendarId,
    String activityType,
    String status,
    double percentComplete,
    LocalDate actualStartDate,
    LocalDate actualFinishDate,
    String primaryConstraintType,
    LocalDate primaryConstraintDate,
    String secondaryConstraintType,
    LocalDate secondaryConstraintDate
) {
}
