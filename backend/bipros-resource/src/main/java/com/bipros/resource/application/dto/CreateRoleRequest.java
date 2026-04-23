package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateRoleRequest(
    @NotBlank(message = "Code is required") String code,
    @NotBlank(message = "Name is required") String name,
    String description,
    @NotNull(message = "Resource type is required") ResourceType resourceType,
    BigDecimal defaultRate,
    String rateUnit,
    BigDecimal budgetedRate,
    BigDecimal actualRate,
    String rateRemarks) {}
