package com.bipros.calendar.application.dto;

import com.bipros.calendar.domain.model.Calendar;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CalendarDetailResponse(
    CalendarResponse calendar,
    List<CalendarWorkWeekResponse> workWeeks,
    List<CalendarExceptionResponse> exceptions
) {
  public static CalendarDetailResponse from(
      Calendar calendar,
      List<CalendarWorkWeekResponse> workWeeks,
      List<CalendarExceptionResponse> exceptions) {
    return new CalendarDetailResponse(
        CalendarResponse.from(calendar),
        workWeeks,
        exceptions);
  }
}
