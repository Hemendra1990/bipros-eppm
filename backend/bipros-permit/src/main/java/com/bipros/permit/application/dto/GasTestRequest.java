package com.bipros.permit.application.dto;

import com.bipros.permit.domain.model.GasTestResult;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record GasTestRequest(
        BigDecimal lelPct,
        BigDecimal o2Pct,
        BigDecimal h2sPpm,
        BigDecimal coPpm,
        @NotNull GasTestResult result,
        String instrumentSerial
) {}
