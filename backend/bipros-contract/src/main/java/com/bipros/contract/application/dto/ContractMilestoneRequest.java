package com.bipros.contract.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContractMilestoneRequest(
    @NotNull UUID contractId,
    String milestoneCode,
    @NotBlank String milestoneName,
    LocalDate targetDate,
    LocalDate actualDate,
    Double paymentPercentage,
    BigDecimal amount
) {}
