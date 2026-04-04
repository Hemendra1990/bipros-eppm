package com.bipros.contract.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ContractorScorecardResponse(
    UUID id,
    UUID contractId,
    String period,
    Double qualityScore,
    Double safetyScore,
    Double progressScore,
    Double paymentComplianceScore,
    Double overallScore,
    String remarks,
    Instant createdAt,
    Instant updatedAt
) {}
