package com.bipros.reporting.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MilestoneRow(
    UUID milestoneId,
    String code,
    String name,
    String milestoneType,
    LocalDate plannedDate,
    LocalDate forecastDate,
    LocalDate actualDate,
    String status,
    long daysSlip,
    BigDecimal ldExposureCrores) {}
