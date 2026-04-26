package com.bipros.contract.application.dto;

import com.bipros.common.web.json.Views;
import com.bipros.contract.domain.model.BillingCycle;
import com.bipros.contract.domain.model.ContractStatus;
import com.bipros.contract.domain.model.ContractType;
import com.fasterxml.jackson.annotation.JsonView;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Money-impacting fields ({@code contractValue}, {@code revisedValue}, payment percentages,
 * VO value, RA bill rolls) carry {@link Views.FinanceConfidential} — non-finance/non-admin
 * callers receive {@code null} via {@link com.bipros.common.web.json.RoleAwareViewAdvice}.
 * Schedule indices ({@code spi}, {@code cpi}) and physical progress remain {@link Views.Public}.
 */
public record ContractResponse(
    UUID id,
    UUID projectId,
    UUID tenderId,
    String contractNumber,
    String loaNumber,
    String contractorName,
    String contractorCode,
    @JsonView(Views.FinanceConfidential.class) BigDecimal contractValue,
    @JsonView(Views.FinanceConfidential.class) BigDecimal revisedValue,
    LocalDate loaDate,
    LocalDate startDate,
    LocalDate completionDate,
    LocalDate revisedCompletionDate,
    Integer dlpMonths,
    @JsonView(Views.FinanceConfidential.class) Double ldRate,
    ContractStatus status,
    ContractType contractType,
    String description,
    String currency,
    LocalDate ntpDate,
    @JsonView(Views.FinanceConfidential.class) BigDecimal mobilisationAdvancePct,
    @JsonView(Views.FinanceConfidential.class) BigDecimal retentionPct,
    @JsonView(Views.FinanceConfidential.class) BigDecimal performanceBgPct,
    @JsonView(Views.FinanceConfidential.class) Integer paymentTermsDays,
    BillingCycle billingCycle,
    // IC-PMS denormalised KPI fields
    String wbsPackageCode,
    String packageDescription,
    LocalDate actualCompletionDate,
    BigDecimal spi,
    BigDecimal cpi,
    BigDecimal physicalProgressAi,
    @JsonView(Views.FinanceConfidential.class) BigDecimal cumulativeRaBillsCrores,
    Integer voNumbersIssued,
    @JsonView(Views.FinanceConfidential.class) BigDecimal voValueCrores,
    BigDecimal performanceScore,
    LocalDate bgExpiry,
    OffsetDateTime kpiRefreshedAt,
    Instant createdAt,
    Instant updatedAt
) {}
