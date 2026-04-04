package com.bipros.contract.application.dto;

import com.bipros.contract.domain.model.ProcurementMethod;
import com.bipros.contract.domain.model.ProcurementPlanStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProcurementPlanResponse(
    UUID id,
    UUID projectId,
    UUID wbsNodeId,
    String planCode,
    String description,
    ProcurementMethod procurementMethod,
    BigDecimal estimatedValue,
    String currency,
    LocalDate targetNitDate,
    LocalDate targetAwardDate,
    ProcurementPlanStatus status,
    String approvalLevel,
    String approvedBy,
    Instant approvedAt,
    Instant createdAt,
    Instant updatedAt
) {}
