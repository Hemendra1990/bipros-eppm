package com.bipros.contract.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record VariationOrderRequest(
    @NotNull UUID contractId,
    @NotBlank String voNumber,
    String description,
    BigDecimal voValue,
    String justification,
    BigDecimal impactOnBudget,
    Integer impactOnScheduleDays,
    String approvedBy
) {}
