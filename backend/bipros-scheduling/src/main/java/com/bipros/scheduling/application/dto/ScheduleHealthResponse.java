package com.bipros.scheduling.application.dto;

import com.bipros.scheduling.domain.model.RiskLevel;
import com.bipros.scheduling.domain.model.ScheduleHealthIndex;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    RiskLevel riskLevel
) {
  public static ScheduleHealthResponse from(ScheduleHealthIndex health) {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Integer> distribution;
    try {
      distribution = mapper.readValue(health.getFloatDistribution(), Map.class);
    } catch (JsonProcessingException e) {
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
        health.getRiskLevel()
    );
  }
}
