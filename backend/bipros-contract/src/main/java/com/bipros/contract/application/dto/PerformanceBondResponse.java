package com.bipros.contract.application.dto;

import com.bipros.contract.domain.model.BondStatus;
import com.bipros.contract.domain.model.BondType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PerformanceBondResponse(
    UUID id,
    UUID contractId,
    BondType bondType,
    BigDecimal bondValue,
    String bankName,
    LocalDate issueDate,
    LocalDate expiryDate,
    BondStatus status,
    long attachmentCount,
    Instant createdAt,
    Instant updatedAt
) {}
