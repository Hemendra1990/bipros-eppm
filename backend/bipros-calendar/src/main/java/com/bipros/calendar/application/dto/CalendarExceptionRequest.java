package com.bipros.calendar.application.dto;

import com.bipros.calendar.domain.model.DayType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CalendarExceptionRequest(
    @NotNull(message = "Exception date is required")
    LocalDate exceptionDate,
    @NotNull(message = "Day type is required")
    DayType dayType,
    String name,
    LocalTime startTime1,
    LocalTime endTime1,
    LocalTime startTime2,
    LocalTime endTime2
) {}
