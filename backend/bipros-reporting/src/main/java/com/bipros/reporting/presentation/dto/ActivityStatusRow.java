package com.bipros.reporting.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivityStatusRow(
    UUID activityId,
    String code,
    String name,
    String wbsCode,
    String wbsName,
    String status,
    String activityType,
    LocalDate plannedStart,
    LocalDate plannedFinish,
    LocalDate actualStart,
    LocalDate actualFinish,
    LocalDate earlyStart,
    LocalDate earlyFinish,
    Double totalFloat,
    Double freeFloat,
    boolean isCritical,
    double pctComplete,
    long daysDelay,
    long daysRemaining) {}
