package com.bipros.contract.application.dto;

import com.bipros.contract.domain.model.ContractStatus;
import com.bipros.contract.domain.model.ContractType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    Instant createdAt,
    Instant updatedAt
) {}
