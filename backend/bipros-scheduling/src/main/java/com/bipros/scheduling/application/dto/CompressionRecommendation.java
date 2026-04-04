package com.bipros.scheduling.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CompressionRecommendation(
    UUID activityId,
    String activityCode,
    Double originalDuration,
    Double newDuration,
    Double durationSaved,
    BigDecimal additionalCost,
    String reason
) {}
