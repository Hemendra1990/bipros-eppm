package com.bipros.calendar.application.dto;

import com.bipros.calendar.domain.model.CalendarWorkWeek;
import com.bipros.calendar.domain.model.DayType;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record CalendarWorkWeekResponse(
    UUID id,
    UUID calendarId,
    DayOfWeek dayOfWeek,
    DayType dayType,
    LocalTime startTime1,
    LocalTime endTime1,
    LocalTime startTime2,
    LocalTime endTime2,
    Double totalWorkHours
) {
  public static CalendarWorkWeekResponse from(CalendarWorkWeek workWeek) {
    return new CalendarWorkWeekResponse(
        workWeek.getId(),
        workWeek.getCalendarId(),
        workWeek.getDayOfWeek(),
        workWeek.getDayType(),
        workWeek.getStartTime1(),
        workWeek.getEndTime1(),
        workWeek.getStartTime2(),
        workWeek.getEndTime2(),
        workWeek.getTotalWorkHours());
  }
}
