package com.bipros.scheduling.application.dto;

import com.bipros.scheduling.domain.model.CompressionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CompressionAnalysisResponse(
    UUID id,
    UUID projectId,
    UUID scenarioId,
    CompressionType analysisType,
    Double originalDuration,
    Double compressedDuration,
    Double durationSaved,
    BigDecimal additionalCost,
    List<CompressionRecommendation> recommendations,
    Instant createdAt,
    Instant updatedAt,
    LocalDate originalFinishDate,
    LocalDate compressedFinishDate
) {}
