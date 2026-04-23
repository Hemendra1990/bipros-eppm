package com.bipros.activity.application.dto;

import com.bipros.activity.domain.model.ActivityType;
import com.bipros.activity.domain.model.DurationType;
import com.bipros.activity.domain.model.PercentCompleteType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.UUID;

public record CreateActivityRequest(
    @NotBlank(message = "Code is required")
    String code,

    @NotBlank(message = "Name is required")
    String name,

    String description,

    @NotNull(message = "Project ID is required")
    UUID projectId,

    @NotNull(message = "WBS Node ID is required")
    UUID wbsNodeId,

    ActivityType activityType,

    DurationType durationType,

    PercentCompleteType percentCompleteType,

    @PositiveOrZero(message = "Original duration must be zero or positive")
    Double originalDuration,

    LocalDate plannedStartDate,

    LocalDate plannedFinishDate,

    UUID calendarId,

    Long chainageFromM,

    Long chainageToM
) {}
