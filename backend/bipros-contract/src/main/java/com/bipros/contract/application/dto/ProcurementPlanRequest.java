package com.bipros.contract.application.dto;

import com.bipros.contract.domain.model.ProcurementMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProcurementPlanRequest(
    @NotNull UUID projectId,
    UUID wbsNodeId,
    @NotBlank String planCode,
    String description,
    @NotNull ProcurementMethod procurementMethod,
    BigDecimal estimatedValue,
    String currency,
    LocalDate targetNitDate,
    LocalDate targetAwardDate,
    String approvalLevel,
    String approvedBy
) {}
