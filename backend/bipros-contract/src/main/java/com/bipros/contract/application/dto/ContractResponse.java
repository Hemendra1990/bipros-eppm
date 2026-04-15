package com.bipros.contract.application.dto;

import com.bipros.contract.domain.model.ContractStatus;
import com.bipros.contract.domain.model.ContractType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ContractResponse(
    UUID id,
    UUID projectId,
    UUID tenderId,
    String contractNumber,
    String loaNumber,
    String contractorName,
    String contractorCode,
    BigDecimal contractValue,
    LocalDate loaDate,
    LocalDate startDate,
    LocalDate completionDate,
    Integer dlpMonths,
    Double ldRate,
    ContractStatus status,
    ContractType contractType,
    // IC-PMS denormalised KPI fields
    String wbsPackageCode,
    String packageDescription,
    LocalDate actualCompletionDate,
    BigDecimal spi,
    BigDecimal cpi,
    BigDecimal physicalProgressAi,
    BigDecimal cumulativeRaBillsCrores,
    Integer voNumbersIssued,
    BigDecimal voValueCrores,
    BigDecimal performanceScore,
    LocalDate bgExpiry,
    OffsetDateTime kpiRefreshedAt,
    Instant createdAt,
    Instant updatedAt
) {}
