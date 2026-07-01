package com.bipros.project.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only aggregation behind the HSE statistics tab. Man-hours are currency-neutral raw numbers;
 * counts exclude CANCELLED issues. {@code lastLtiDate} is null when no LTI is logged (in which case
 * the without-LTI figures equal the Worked totals).
 */
public record HseStatisticsResponse(
    BigDecimal manHoursWorked,
    BigDecimal manHoursWithoutLti,
    long projectDaysWorked,
    long projectDaysWithoutLti,
    BigDecimal kmDistanceDriven,
    long mtcCount,
    long propertyDamageCount,
    long nearMissCount,
    long fatalityCount,
    LocalDate lastLtiDate,
    BigDecimal calendarHoursPerDay
) {}
