package com.bipros.contract.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record BidSubmissionRequest(
    @NotNull UUID tenderId,
    @NotBlank String bidderName,
    String bidderCode,
    Double technicalScore,
    BigDecimal financialBid,
    String evaluationRemarks
) {}
