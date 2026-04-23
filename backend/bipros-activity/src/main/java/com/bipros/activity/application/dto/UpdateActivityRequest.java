package com.bipros.activity.application.dto;

import com.bipros.activity.domain.model.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateActivityRequest(
    @NotBlank(message = "Name is required")
    String name,

    String description,

    UUID wbsNodeId,

    ActivityType activityType,

    DurationType durationType,

    PercentCompleteType percentCompleteType,

    @PositiveOrZero(message = "Original duration must be zero or positive")
    Double originalDuration,

    @PositiveOrZero(message = "Remaining duration must be zero or positive")
    Double remainingDuration,

    ActivityStatus status,

    @PositiveOrZero(message = "Percent complete must be zero or positive")
    @NotNull(message = "Percent complete is required")
    Double percentComplete,

    @PositiveOrZero(message = "Physical percent complete must be zero or positive")
    Double physicalPercentComplete,

    LocalDate actualStartDate,

    LocalDate actualFinishDate,

    UUID calendarId,

    ConstraintType primaryConstraintType,

    LocalDate primaryConstraintDate,

    ConstraintType secondaryConstraintType,

    LocalDate secondaryConstraintDate,

    LocalDate suspendDate,

    LocalDate resumeDate,

    String notes,

    Long chainageFromM,

    Long chainageToM
) {}
