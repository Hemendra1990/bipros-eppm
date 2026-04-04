package com.bipros.contract.application.dto;

import com.bipros.contract.domain.model.BondType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PerformanceBondRequest(
    @NotNull UUID contractId,
    @NotNull BondType bondType,
    BigDecimal bondValue,
    String bankName,
    LocalDate issueDate,
    LocalDate expiryDate
) {}
