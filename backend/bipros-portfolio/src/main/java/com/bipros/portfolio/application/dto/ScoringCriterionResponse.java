package com.bipros.portfolio.application.dto;

import com.bipros.portfolio.domain.ScoringCriterion;

import java.time.Instant;
import java.util.UUID;

public record ScoringCriterionResponse(
    UUID id,
    UUID scoringModelId,
    String name,
    Double weight,
    Double minScore,
    Double maxScore,
    Integer sortOrder,
    Instant createdAt,
    Instant updatedAt) {

  public static ScoringCriterionResponse from(ScoringCriterion criterion) {
    return new ScoringCriterionResponse(
        criterion.getId(),
        criterion.getScoringModelId(),
        criterion.getName(),
        criterion.getWeight(),
        criterion.getMinScore(),
        criterion.getMaxScore(),
        criterion.getSortOrder(),
        criterion.getCreatedAt(),
        criterion.getUpdatedAt());
  }
}
