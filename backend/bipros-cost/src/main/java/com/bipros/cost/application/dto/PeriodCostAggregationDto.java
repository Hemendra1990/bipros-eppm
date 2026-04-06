package com.bipros.cost.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PeriodCostAggregationDto(
        UUID periodId,
        String periodName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal budget,
        BigDecimal actual,
        BigDecimal variance,
        BigDecimal earnedValue,
        BigDecimal plannedValue
) {}
