package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProjectLabourDeploymentRequest(
    @NotNull UUID designationId,
    @NotNull @PositiveOrZero Integer workerCount,
    @PositiveOrZero BigDecimal actualDailyRate,
    @Size(max = 500) String notes
) {}
