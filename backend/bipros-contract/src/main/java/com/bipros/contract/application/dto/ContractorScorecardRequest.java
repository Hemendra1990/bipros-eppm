package com.bipros.contract.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ContractorScorecardRequest(
    @NotNull UUID contractId,
    @NotBlank String period,
    Double qualityScore,
    Double safetyScore,
    Double progressScore,
    Double paymentComplianceScore,
    Double overallScore,
    String remarks
) {}
