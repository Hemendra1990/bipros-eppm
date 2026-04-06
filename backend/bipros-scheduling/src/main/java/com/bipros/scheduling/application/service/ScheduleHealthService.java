package com.bipros.scheduling.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.scheduling.application.dto.ScheduleHealthResponse;
import com.bipros.scheduling.domain.model.RiskLevel;
import com.bipros.scheduling.domain.model.ScheduleActivityResult;
import com.bipros.scheduling.domain.model.ScheduleHealthIndex;
import com.bipros.scheduling.domain.model.ScheduleResult;
import com.bipros.scheduling.domain.repository.ScheduleActivityResultRepository;
import com.bipros.scheduling.domain.repository.ScheduleHealthIndexRepository;
import com.bipros.scheduling.domain.repository.ScheduleResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ScheduleHealthService {

  private final ScheduleHealthIndexRepository scheduleHealthIndexRepository;
  private final ScheduleResultRepository scheduleResultRepository;
  private final ScheduleActivityResultRepository scheduleActivityResultRepository;

  @Transactional(readOnly = true)
  public ScheduleHealthResponse getLatestHealth(UUID projectId) {
    log.debug("Fetching latest schedule health for project: id={}", projectId);

    return scheduleHealthIndexRepository.findTopByProjectIdOrderByCreatedAtDesc(projectId)
        .map(ScheduleHealthResponse::from)
        .orElse(new ScheduleHealthResponse(
            null, projectId, null, 0, 0, 0, 0.0, 0.0, Map.of(), RiskLevel.LOW));
  }

  public ScheduleHealthResponse calculateHealth(UUID scheduleResultId) {
    log.info("Calculating schedule health for schedule result: id={}", scheduleResultId);

    ScheduleResult scheduleResult = scheduleResultRepository.findById(scheduleResultId)
        .orElseThrow(() -> new ResourceNotFoundException("ScheduleResult", scheduleResultId));

    List<ScheduleActivityResult> activityResults = scheduleActivityResultRepository
        .findByScheduleResultId(scheduleResultId);

    if (activityResults.isEmpty()) {
      throw new IllegalArgumentException("No activity results found for schedule result: " + scheduleResultId);
    }

    // Calculate metrics
    int totalActivities = activityResults.size();
    int criticalActivities = (int) activityResults.stream()
        .filter(r -> r.getIsCritical() != null && r.getIsCritical())
        .count();

    int nearCriticalActivities = (int) activityResults.stream()
        .filter(r -> r.getTotalFloat() != null && r.getTotalFloat() > 0 && r.getTotalFloat() <= 5)
        .count();

    double totalFloatAverage = activityResults.stream()
        .mapToDouble(r -> r.getTotalFloat() != null ? r.getTotalFloat() : 0)
        .average()
        .orElse(0);

    // Build float distribution
    Map<String, Integer> floatDistribution = buildFloatDistribution(activityResults);

    // Calculate health score
    double criticalPct = (double) criticalActivities / totalActivities;
    double nearCriticalPct = (double) nearCriticalActivities / totalActivities;
    double durationVariance = calculateDurationVariance(scheduleResult);

    double healthScore = 100 - (criticalPct * 40) - (nearCriticalPct * 20) - Math.max(0, durationVariance * 40);
    healthScore = Math.max(0, Math.min(100, healthScore));

    // Determine risk level
    RiskLevel riskLevel = determineRiskLevel(healthScore);

    // Save health index
    ScheduleHealthIndex healthIndex = ScheduleHealthIndex.builder()
        .scheduleResultId(scheduleResultId)
        .projectId(scheduleResult.getProjectId())
        .totalActivities(totalActivities)
        .criticalActivities(criticalActivities)
        .nearCriticalActivities(nearCriticalActivities)
        .totalFloatAverage(totalFloatAverage)
        .healthScore(healthScore)
        .floatDistribution(serializeFloatDistribution(floatDistribution))
        .riskLevel(riskLevel)
        .build();

    ScheduleHealthIndex saved = scheduleHealthIndexRepository.save(healthIndex);
    log.info("Schedule health calculated: scheduleResultId={}, healthScore={}, riskLevel={}",
        scheduleResultId, healthScore, riskLevel);

    return ScheduleHealthResponse.from(saved);
  }

  private Map<String, Integer> buildFloatDistribution(List<ScheduleActivityResult> activityResults) {
    Map<String, Integer> distribution = new HashMap<>();
    distribution.put("zero", 0);
    distribution.put("1to5", 0);
    distribution.put("6to10", 0);
    distribution.put("10plus", 0);

    for (ScheduleActivityResult result : activityResults) {
      double totalFloat = result.getTotalFloat() != null ? result.getTotalFloat() : 0;

      if (totalFloat == 0) {
        distribution.put("zero", distribution.get("zero") + 1);
      } else if (totalFloat <= 5) {
        distribution.put("1to5", distribution.get("1to5") + 1);
      } else if (totalFloat <= 10) {
        distribution.put("6to10", distribution.get("6to10") + 1);
      } else {
        distribution.put("10plus", distribution.get("10plus") + 1);
      }
    }

    return distribution;
  }

  private double calculateDurationVariance(ScheduleResult scheduleResult) {
    // Simple variance: (actual - baseline) / baseline
    // For now, assume baseline is original estimate; comparing to current
    long daysDifference = java.time.temporal.ChronoUnit.DAYS.between(
        scheduleResult.getProjectStartDate(),
        scheduleResult.getProjectFinishDate()
    );

    // Assume 5% variance per month is acceptable
    double acceptableVariance = 0.05;
    double monthCount = daysDifference / 30.0;

    // If duration is reasonable, variance is low
    return Math.max(0, (monthCount * 0.02) - acceptableVariance);
  }

  private RiskLevel determineRiskLevel(double healthScore) {
    if (healthScore >= 80) {
      return RiskLevel.LOW;
    } else if (healthScore >= 60) {
      return RiskLevel.MEDIUM;
    } else if (healthScore >= 40) {
      return RiskLevel.HIGH;
    } else {
      return RiskLevel.CRITICAL;
    }
  }

  private String serializeFloatDistribution(Map<String, Integer> distribution) {
    try {
      return new ObjectMapper().writeValueAsString(distribution);
    } catch (Exception e) {
      log.error("Error serializing float distribution", e);
      return "{}";
    }
  }
}
