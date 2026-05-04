package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PoolEntryInput(
    @NotNull(message = "Resource ID is required") UUID resourceId,
    BigDecimal rateOverride,
    Double availabilityOverride,
    String customUnit,
    String notes
) {}
