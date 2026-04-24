package com.bipros.reporting.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DelayedProjectRow(
    UUID projectId,
    String projectCode,
    String projectName,
    LocalDate plannedFinish,
    LocalDate forecastFinish,
    long daysDelayed,
    double spi,
    String rag) {}
