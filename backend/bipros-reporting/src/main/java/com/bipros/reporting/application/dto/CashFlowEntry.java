package com.bipros.reporting.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CashFlowEntry(
    String period,
    BigDecimal planned,
    BigDecimal actual,
    BigDecimal forecast,
    BigDecimal cumulativePlanned,
    BigDecimal cumulativeActual,
    BigDecimal cumulativeForecast) {}
