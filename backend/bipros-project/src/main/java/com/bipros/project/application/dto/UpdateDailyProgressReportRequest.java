package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * All fields except chainage, BOQ link, weather, and remarks are required — full replacement
 * of the row, not a sparse patch. Keeps the BOQ delta math unambiguous (old row → new row in
 * one transactional write).
 */
public record UpdateDailyProgressReportRequest(
    @NotNull LocalDate reportDate,

    UUID supervisorResourceId,

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
