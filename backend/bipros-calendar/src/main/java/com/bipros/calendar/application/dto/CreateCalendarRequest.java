package com.bipros.calendar.application.dto;

import com.bipros.calendar.domain.model.CalendarType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateCalendarRequest(
    @NotBlank(message = "Calendar name is required")
    String name,
    String description,
    @NotNull(message = "Calendar type is required")
    CalendarType calendarType,
    UUID projectId,
    UUID resourceId,
    UUID parentCalendarId,
    @Positive(message = "Standard work hours per day must be positive")
    Double standardWorkHoursPerDay,
    @Positive(message = "Standard work days per week must be positive")
    Integer standardWorkDaysPerWeek
) {}
