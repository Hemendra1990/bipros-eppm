package com.bipros.contract.application.dto;

import com.bipros.contract.domain.model.BillingCycle;
import com.bipros.contract.domain.model.ContractType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContractRequest(
    @NotNull UUID projectId,
    UUID tenderId,
    @NotBlank String contractNumber,
    String loaNumber,
    @NotBlank String contractorName,
    String contractorCode,
    BigDecimal contractValue,
    BigDecimal revisedValue,
    LocalDate loaDate,
    LocalDate startDate,
    LocalDate completionDate,
    LocalDate revisedCompletionDate,
    Integer dlpMonths,
    Double ldRate,
    @NotNull ContractType contractType,
    String description,
    String currency,
    LocalDate ntpDate,
    BigDecimal mobilisationAdvancePct,
    BigDecimal retentionPct,
    BigDecimal performanceBgPct,
    Integer paymentTermsDays,
    BillingCycle billingCycle
) {}
