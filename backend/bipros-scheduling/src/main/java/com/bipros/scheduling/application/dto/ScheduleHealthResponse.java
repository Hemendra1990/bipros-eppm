package com.bipros.scheduling.application.dto;

import com.bipros.scheduling.domain.model.RiskLevel;
import com.bipros.scheduling.domain.model.ScheduleHealthIndex;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record ScheduleHealthResponse(
    UUID id,
    UUID projectId,
    UUID scheduleResultId,
    Integer totalActivities,
    Integer criticalActivities,
    Integer nearCriticalActivities,
    Double totalFloatAverage,
    Double healthScore,
    Map<String, Integer> floatDistribution,
    RiskLevel riskLevel,
    Double missingLogicPct,
    Double highFloatPct,
    Double deadlineSlipRatio,
    Integer deadlineSlipDays,
    LocalDate plannedFinish,
    LocalDate scheduledFinish,
    Instant computedAt,
    Boolean stale
) {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static ScheduleHealthResponse from(ScheduleHealthIndex health) {
    return from(health, false);
  }

  public static ScheduleHealthResponse from(ScheduleHealthIndex health, boolean stale) {
    Map<String, Integer> distribution;
    try {
      distribution = MAPPER.readValue(health.getFloatDistribution(), Map.class);
    } catch (JsonProcessingException | IllegalArgumentException | NullPointerException e) {
      distribution = Map.of();
    }
    return new ScheduleHealthResponse(
        health.getId(),
        health.getProjectId(),
        health.getScheduleResultId(),
        health.getTotalActivities(),
        health.getCriticalActivities(),
        health.getNearCriticalActivities(),
        health.getTotalFloatAverage(),
        health.getHealthScore(),
        distribution,
        health.getRiskLevel(),
        health.getMissingLogicPct(),
        health.getHighFloatPct(),
        health.getDeadlineSlipRatio(),
        health.getDeadlineSlipDays(),
        health.getPlannedFinishDate(),
        health.getScheduledFinishDate(),
        health.getCreatedAt(),
        stale
    );
  }
}
