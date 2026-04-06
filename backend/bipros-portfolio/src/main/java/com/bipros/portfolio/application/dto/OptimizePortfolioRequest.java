package com.bipros.portfolio.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OptimizePortfolioRequest(
    @NotNull @DecimalMin("0.0") BigDecimal budgetLimit,
    @Size(max = 100) List<UUID> mandatoryProjectIds
) {}
