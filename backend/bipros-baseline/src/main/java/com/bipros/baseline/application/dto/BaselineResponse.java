package com.bipros.baseline.application.dto;

import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BaselineResponse(
    UUID id,
    UUID projectId,
    String name,
    String description,
    BaselineType baselineType,
    LocalDate baselineDate,
    Boolean isActive,
    Integer totalActivities,
    BigDecimal totalCost,
    Double projectDuration,
    LocalDate projectStartDate,
    LocalDate projectFinishDate,
    Instant createdAt,
    Instant updatedAt) {

  public static BaselineResponse from(Baseline baseline) {
    return new BaselineResponse(
        baseline.getId(),
        baseline.getProjectId(),
        baseline.getName(),
        baseline.getDescription(),
        baseline.getBaselineType(),
        baseline.getBaselineDate(),
        baseline.getIsActive(),
        baseline.getTotalActivities(),
        baseline.getTotalCost(),
        baseline.getProjectDuration(),
        baseline.getProjectStartDate(),
        baseline.getProjectFinishDate(),
        baseline.getCreatedAt(),
        baseline.getUpdatedAt());
  }
}
