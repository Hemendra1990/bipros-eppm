package com.bipros.contract.application.dto;

import com.bipros.contract.domain.model.TenderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TenderResponse(
    UUID id,
    UUID procurementPlanId,
    UUID projectId,
    String tenderNumber,
    LocalDate nitDate,
    String scope,
    BigDecimal estimatedValue,
    BigDecimal emdAmount,
    Integer completionPeriodDays,
    LocalDate bidDueDate,
    LocalDate bidOpenDate,
    TenderStatus status,
    UUID awardedContractId,
    Instant createdAt,
    Instant updatedAt
) {}
