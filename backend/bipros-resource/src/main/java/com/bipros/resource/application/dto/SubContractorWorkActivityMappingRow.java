package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record SubContractorWorkActivityMappingRow(
    UUID id,

    @NotNull(message = "workActivityId is required")
    UUID workActivityId,

    String workActivityName,

    String unit,

    @PositiveOrZero(message = "ratePerUnit must be zero or positive")
    BigDecimal ratePerUnit,

    @PositiveOrZero(message = "outputPerDay must be zero or positive")
    BigDecimal outputPerDay
) {
}
