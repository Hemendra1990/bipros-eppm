package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ProductivityNormType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateProductivityNormRequest(
    @NotNull(message = "normType is required")
    ProductivityNormType normType,

    @NotBlank(message = "activityName is required")
    String activityName,

    @NotBlank(message = "unit is required")
    String unit,

    @PositiveOrZero BigDecimal outputPerManPerDay,

    @PositiveOrZero BigDecimal outputPerHour,

    @PositiveOrZero Integer crewSize,

    @PositiveOrZero BigDecimal outputPerDay,

    @PositiveOrZero Double workingHoursPerDay,

    @PositiveOrZero BigDecimal fuelLitresPerHour,

    String equipmentSpec,

    String remarks
) {}
