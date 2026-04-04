package com.bipros.calendar.application.dto;

import com.bipros.calendar.domain.model.Calendar;
import com.bipros.calendar.domain.model.CalendarType;

import java.time.Instant;
import java.util.UUID;

public record CalendarResponse(
    UUID id,
    String name,
    String description,
    CalendarType calendarType,
    UUID projectId,
    UUID resourceId,
    UUID parentCalendarId,
    Boolean isDefault,
    Double standardWorkHoursPerDay,
    Integer standardWorkDaysPerWeek,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy
) {
  public static CalendarResponse from(Calendar calendar) {
    return new CalendarResponse(
        calendar.getId(),
        calendar.getName(),
        calendar.getDescription(),
        calendar.getCalendarType(),
        calendar.getProjectId(),
        calendar.getResourceId(),
        calendar.getParentCalendarId(),
        calendar.getIsDefault(),
        calendar.getStandardWorkHoursPerDay(),
        calendar.getStandardWorkDaysPerWeek(),
        calendar.getCreatedAt(),
        calendar.getUpdatedAt(),
        calendar.getCreatedBy(),
        calendar.getUpdatedBy());
  }
}
