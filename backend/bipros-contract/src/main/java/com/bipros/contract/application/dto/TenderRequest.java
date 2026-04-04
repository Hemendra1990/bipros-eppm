package com.bipros.contract.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TenderRequest(
    @NotNull UUID procurementPlanId,
    @NotNull UUID projectId,
    @NotBlank String tenderNumber,
    LocalDate nitDate,
    String scope,
    BigDecimal estimatedValue,
    BigDecimal emdAmount,
    Integer completionPeriodDays,
    LocalDate bidDueDate,
    LocalDate bidOpenDate
) {}
