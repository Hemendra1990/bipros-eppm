package com.bipros.scheduling.application.dto;

import com.bipros.scheduling.domain.model.SchedulingOption;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ScheduleRequest(
    @NotNull(message = "projectId is required")
    UUID projectId,
    SchedulingOption option
) {
}
