package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateDailyProgressReportRequest(
    @NotNull LocalDate reportDate,

    @NotBlank String supervisorName,

    @PositiveOrZero Long chainageFromM,
    @PositiveOrZero Long chainageToM,

    @NotBlank String activityName,

    UUID wbsNodeId,

    String boqItemNo,

    @NotBlank String unit,

    @NotNull @Positive BigDecimal qtyExecuted,

    String weatherCondition,

    String remarks
) {}
