package com.bipros.contract.application.dto;

import com.bipros.contract.domain.model.VariationOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VariationOrderResponse(
    UUID id,
    UUID contractId,
    String voNumber,
    String description,
    BigDecimal voValue,
    String justification,
    VariationOrderStatus status,
    BigDecimal impactOnBudget,
    Integer impactOnScheduleDays,
    String approvedBy,
    Instant approvedAt,
    long attachmentCount,
    Instant createdAt,
    Instant updatedAt
) {}
