package com.bipros.reporting.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SCurveDataPoint(
    LocalDate date,
    BigDecimal plannedValue,
    BigDecimal earnedValue,
    BigDecimal actualCost) {}
