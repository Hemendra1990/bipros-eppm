package com.bipros.portfolio.application.dto;

import com.bipros.portfolio.domain.ScoringModel;

import java.time.Instant;
import java.util.UUID;

public record ScoringModelResponse(
    UUID id,
    String name,
    String description,
    Boolean isDefault,
    Instant createdAt,
    Instant updatedAt) {

  public static ScoringModelResponse from(ScoringModel scoringModel) {
    return new ScoringModelResponse(
        scoringModel.getId(),
        scoringModel.getName(),
        scoringModel.getDescription(),
        scoringModel.getIsDefault(),
        scoringModel.getCreatedAt(),
        scoringModel.getUpdatedAt());
  }
}
