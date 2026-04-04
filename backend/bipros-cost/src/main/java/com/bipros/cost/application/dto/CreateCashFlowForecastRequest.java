package com.bipros.cost.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCashFlowForecastRequest(
        @NotNull(message = "Project ID is required")
        UUID projectId,

        @NotBlank(message = "Period is required")
        String period,

        BigDecimal plannedAmount,

        BigDecimal actualAmount,

        BigDecimal forecastAmount,

        BigDecimal cumulativePlanned,

        BigDecimal cumulativeActual,

        BigDecimal cumulativeForecast
) {}
