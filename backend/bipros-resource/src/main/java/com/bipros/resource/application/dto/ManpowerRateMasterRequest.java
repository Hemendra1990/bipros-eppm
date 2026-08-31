package com.bipros.resource.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ManpowerRateMasterRequest(
    @NotNull(message = "Role is required") UUID roleId,
    @NotNull(message = "Category is required") UUID categoryId,
    @NotNull(message = "Grade is required") UUID gradeId,

    @NotBlank(message = "Unit is required")
    @Size(max = 30, message = "Unit must be at most 30 characters")
    String unit,

    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.0", message = "Rate must be non-negative")
    BigDecimal rate,

    Boolean active
) {}
