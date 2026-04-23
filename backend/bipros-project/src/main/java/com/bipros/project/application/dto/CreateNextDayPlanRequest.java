package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateNextDayPlanRequest(
    @NotNull LocalDate reportDate,

    @NotBlank String nextDayActivity,

    @PositiveOrZero Long chainageFromM,
    @PositiveOrZero Long chainageToM,

    BigDecimal targetQty,
    String unit,
    String concerns,
    String actionBy,
    LocalDate dueDate
) {}
