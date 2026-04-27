package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateDailyActivityResourceOutputRequest(
    @NotNull(message = "outputDate is required") LocalDate outputDate,

    @NotNull(message = "activityId is required") UUID activityId,

    @NotNull(message = "resourceId is required") UUID resourceId,

    @NotNull(message = "qtyExecuted is required")
    @Positive(message = "qtyExecuted must be > 0")
    BigDecimal qtyExecuted,

    /** Optional — service mirrors from the linked WorkActivity's defaultUnit when blank. */
    @Size(max = 20)
    String unit,

    @PositiveOrZero(message = "hoursWorked must be >= 0")
    Double hoursWorked,

    @PositiveOrZero(message = "daysWorked must be >= 0")
    Double daysWorked,

    @Size(max = 1000)
    String remarks
) {}
