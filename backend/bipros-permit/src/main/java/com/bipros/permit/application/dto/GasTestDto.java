package com.bipros.permit.application.dto;

import com.bipros.permit.domain.model.GasTestResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GasTestDto(
        UUID id,
        BigDecimal lelPct,
        BigDecimal o2Pct,
        BigDecimal h2sPpm,
        BigDecimal coPpm,
        GasTestResult result,
        UUID testedBy,
        Instant testedAt,
        String instrumentSerial
) {}
