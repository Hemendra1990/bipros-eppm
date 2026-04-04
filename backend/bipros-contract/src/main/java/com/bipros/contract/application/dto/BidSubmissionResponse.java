package com.bipros.contract.application.dto;

import com.bipros.contract.domain.model.BidSubmissionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BidSubmissionResponse(
    UUID id,
    UUID tenderId,
    String bidderName,
    String bidderCode,
    Double technicalScore,
    BigDecimal financialBid,
    BidSubmissionStatus status,
    String evaluationRemarks,
    Instant createdAt,
    Instant updatedAt
) {}
