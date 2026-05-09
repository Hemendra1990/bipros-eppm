package com.bipros.resource.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EquipmentRateMasterRequest(
    @NotBlank(message = "Equipment name is required")
    @Size(max = 200, message = "Equipment name must be at most 200 characters")
    String equipmentName,

    @NotBlank(message = "Make is required")
    @Size(max = 100, message = "Make must be at most 100 characters")
    String make,

    @NotBlank(message = "Model is required")
    @Size(max = 100, message = "Model must be at most 100 characters")
    String model,

    @NotBlank(message = "Unit is required")
    @Size(max = 30, message = "Unit must be at most 30 characters")
    String unit,

    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.0", message = "Rate must be non-negative")
    BigDecimal rate,

    Boolean active
) {}
