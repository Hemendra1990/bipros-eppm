package com.bipros.scheduling.application.dto;

import com.bipros.scheduling.domain.model.ScenarioStatus;
import com.bipros.scheduling.domain.model.ScenarioType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ScheduleScenarioResponse(
    UUID id,
    UUID projectId,
    String scenarioName,
    String description,
    ScenarioType scenarioType,
    Double projectDuration,
    Double criticalPathLength,
    BigDecimal totalCost,
    String modifiedActivities,
    ScenarioStatus status,
    Instant createdAt,
    Instant updatedAt
) {
  public static ScheduleScenarioResponse from(com.bipros.scheduling.domain.model.ScheduleScenario scenario) {
    return new ScheduleScenarioResponse(
        scenario.getId(),
        scenario.getProjectId(),
        scenario.getScenarioName(),
        scenario.getDescription(),
        scenario.getScenarioType(),
        scenario.getProjectDuration(),
        scenario.getCriticalPathLength(),
        scenario.getTotalCost(),
        scenario.getModifiedActivities(),
        scenario.getStatus(),
        scenario.getCreatedAt(),
        scenario.getUpdatedAt()
    );
  }
}
