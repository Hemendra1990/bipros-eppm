package com.bipros.cost.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateRaBillRequest(
        @NotNull(message = "Project ID is required")
        UUID projectId,

        UUID contractId,

        String wbsPackageCode,

        @NotBlank(message = "Bill number is required")
        String billNumber,

        @NotNull(message = "Bill period from is required")
        LocalDate billPeriodFrom,

        @NotNull(message = "Bill period to is required")
        LocalDate billPeriodTo,

        @NotNull(message = "Gross amount is required")
        @Positive(message = "Gross amount must be positive")
        BigDecimal grossAmount,

        BigDecimal deductions,
        BigDecimal mobAdvanceRecovery,
        BigDecimal retention5Pct,
        BigDecimal tds2Pct,
        BigDecimal gst18Pct,

        @NotNull(message = "Net amount is required")
        BigDecimal netAmount,

        BigDecimal contractorClaimedPercent,

        String remarks
) {}
