package com.bipros.reporting.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CashFlowOutlookPoint(
    String yearMonth,
    BigDecimal plannedOutflowRaw,
    BigDecimal plannedInflowRaw,
    BigDecimal netRaw,
    BigDecimal cumulativeRaw,
    String currency) {}
