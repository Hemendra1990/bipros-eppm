package com.bipros.calendar.application.dto;

import com.bipros.calendar.domain.model.DayType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record CalendarWorkWeekRequest(
    @NotNull(message = "Day of week is required")
    String dayOfWeek,
    @NotNull(message = "Day type is required")
    DayType dayType,
    LocalTime startTime1,
    LocalTime endTime1,
    LocalTime startTime2,
    LocalTime endTime2
) {}
