package com.bipros.contract.application.dto;

import com.bipros.contract.domain.model.MilestoneStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ContractMilestoneResponse(
    UUID id,
    UUID contractId,
    String milestoneCode,
    String milestoneName,
    LocalDate targetDate,
    LocalDate actualDate,
    Double paymentPercentage,
    BigDecimal amount,
    MilestoneStatus status,
    long attachmentCount,
    Instant createdAt,
    Instant updatedAt
) {}
