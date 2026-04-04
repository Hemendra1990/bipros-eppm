package com.bipros.calendar.application.dto;

import com.bipros.calendar.domain.model.CalendarException;
import com.bipros.calendar.domain.model.DayType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CalendarExceptionResponse(
    UUID id,
    UUID calendarId,
    LocalDate exceptionDate,
    DayType dayType,
    String name,
    LocalTime startTime1,
    LocalTime endTime1,
    LocalTime startTime2,
    LocalTime endTime2,
    Double totalWorkHours,
    Instant createdAt,
    Instant updatedAt
) {
  public static CalendarExceptionResponse from(CalendarException exception) {
    return new CalendarExceptionResponse(
        exception.getId(),
        exception.getCalendarId(),
        exception.getExceptionDate(),
        exception.getDayType(),
        exception.getName(),
        exception.getStartTime1(),
        exception.getEndTime1(),
        exception.getStartTime2(),
        exception.getEndTime2(),
        exception.getTotalWorkHours(),
        exception.getCreatedAt(),
        exception.getUpdatedAt());
  }
}
