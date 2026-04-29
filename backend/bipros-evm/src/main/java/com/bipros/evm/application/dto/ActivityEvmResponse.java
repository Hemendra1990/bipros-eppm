package com.bipros.evm.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ActivityEvmResponse(
        UUID activityId,
        UUID projectId,
        BigDecimal bac,
        BigDecimal pv,
        BigDecimal ev,
        BigDecimal ac,
        BigDecimal cv,
        BigDecimal sv,
        Double cpi,
        Double spi,
        Double percentComplete,
        String earnedValueTechnique
) {}
