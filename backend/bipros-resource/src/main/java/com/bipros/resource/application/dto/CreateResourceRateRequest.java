package com.bipros.resource.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateResourceRateRequest(
    @NotBlank(message = "Rate type is required") String rateType,
    @NotNull(message = "Price per unit is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        BigDecimal pricePerUnit,
    @NotNull(message = "Effective date is required") LocalDate effectiveDate,
    Double maxUnitsPerTime) {}
