package com.bipros.scheduling.application.service;

import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
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

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ScheduleHealthService {

  /** DCMA high-float test ceiling, in working days. */
  private static final double HIGH_FLOAT_DAYS = 44.0;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final double W_MISSING_LOGIC = 40.0;
  private static final double W_HIGH_FLOAT = 25.0;
  private static final double W_DEADLINE_SLIP = 25.0;
  private static final double W_CRITICAL = 10.0;

  private final ScheduleHealthIndexRepository scheduleHealthIndexRepository;
  private final ScheduleResultRepository scheduleResultRepository;
  private final ScheduleActivityResultRepository scheduleActivityResultRepository;
  private final ActivityRelationshipRepository activityRelationshipRepository;
  private final ActivityRepository activityRepository;
  private final ProjectRepository projectRepository;

  @Transactional(readOnly = true)
  public ScheduleHealthResponse getLatestHealth(UUID projectId) {
    log.debug("Fetching latest schedule health for project: id={}", projectId);
    return scheduleHealthIndexRepository.findTopByProjectIdOrderByCreatedAtDesc(projectId)
        .map(idx -> ScheduleHealthResponse.from(idx, isStale(projectId, idx)))
        .orElse(null); // null → frontend shows "Run a schedule first"
  }

  private boolean isStale(UUID projectId, ScheduleHealthIndex idx) {
    if (idx.getCreatedAt() == null) return false;
    // The schedule run writes Activity rows in the same transaction just before the health index
    // is created; allow a small tolerance so the run's own writes are not flagged as a user edit.
    java.time.Instant baseline = idx.getCreatedAt().plusSeconds(5);
    return activityRepository.existsByProjectIdAndUpdatedAtAfter(projectId, baseline)
        || activityRelationshipRepository.existsByProjectIdAndUpdatedAtAfter(projectId, baseline);
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

    int totalActivities = activityResults.size();
    int criticalActivities = (int) activityResults.stream()
        .filter(r -> Boolean.TRUE.equals(r.getIsCritical())).count();
    // Near-critical EXCLUDES criticals so Critical / Near-Critical / Healthy partition cleanly.
    int nearCriticalActivities = (int) activityResults.stream()
        .filter(r -> !Boolean.TRUE.equals(r.getIsCritical()))
        .filter(r -> r.getTotalFloat() != null && r.getTotalFloat() > 0 && r.getTotalFloat() <= 5)
        .count();
    double totalFloatAverage = activityResults.stream()
        .mapToDouble(r -> r.getTotalFloat() != null ? r.getTotalFloat() : 0).average().orElse(0);

    Map<String, Integer> floatDistribution = buildFloatDistribution(activityResults);

    // --- Schedule-quality factors ---
    double missingLogicPct = computeMissingLogicPct(scheduleResult.getProjectId(), activityResults);
    double highFloatPct = (double) activityResults.stream()
        .filter(r -> r.getTotalFloat() != null && r.getTotalFloat() > HIGH_FLOAT_DAYS).count()
        / totalActivities;
    DeadlineSlip slip = computeDeadlineSlip(scheduleResult);
    double criticalPct = (double) criticalActivities / totalActivities;

    double healthScore = 100.0
        - (missingLogicPct * W_MISSING_LOGIC)
        - (highFloatPct * W_HIGH_FLOAT)
        - (slip.ratio() * W_DEADLINE_SLIP)
        - (criticalPct * W_CRITICAL);
    healthScore = Math.max(0, Math.min(100, healthScore));

    RiskLevel riskLevel = determineRiskLevel(healthScore);

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
        .missingLogicPct(missingLogicPct)
        .highFloatPct(highFloatPct)
        .deadlineSlipRatio(slip.ratio())
        .deadlineSlipDays((int) slip.days())
        .plannedFinishDate(slip.plannedFinish())
        .scheduledFinishDate(scheduleResult.getProjectFinishDate())
        .build();

    ScheduleHealthIndex saved = scheduleHealthIndexRepository.save(healthIndex);
    log.info("Schedule health calculated: scheduleResultId={}, healthScore={}, riskLevel={}",
        scheduleResultId, healthScore, riskLevel);
    return ScheduleHealthResponse.from(saved, false);
  }

  /** % of activities that are neither a predecessor nor a successor in any relationship (open/dangling). */
  private double computeMissingLogicPct(UUID projectId, List<ScheduleActivityResult> activityResults) {
    Set<UUID> linked = new HashSet<>();
    for (ActivityRelationship rel : activityRelationshipRepository.findByProjectId(projectId)) {
      linked.add(rel.getPredecessorActivityId());
      linked.add(rel.getSuccessorActivityId());
    }
    long open = activityResults.stream()
        .filter(r -> {
          if (r.getActivityId() == null) {
            log.warn("ScheduleActivityResult with null activityId counted as open (missing logic)");
            return true;
          }
          return !linked.contains(r.getActivityId());
        })
        .count();
    return (double) open / activityResults.size();
  }

  private record DeadlineSlip(double ratio, long days, LocalDate plannedFinish) {}

  private DeadlineSlip computeDeadlineSlip(ScheduleResult scheduleResult) {
    Project project = projectRepository.findById(scheduleResult.getProjectId()).orElse(null);
    LocalDate plannedStart = project != null ? project.getPlannedStartDate() : null;
    LocalDate plannedFinish = project != null ? project.getPlannedFinishDate() : null;
    LocalDate scheduledFinish = scheduleResult.getProjectFinishDate();
    if (plannedStart == null || plannedFinish == null || scheduledFinish == null
        || !plannedFinish.isAfter(plannedStart)) {
      return new DeadlineSlip(0.0, 0, plannedFinish);
    }
    long plannedDuration = ChronoUnit.DAYS.between(plannedStart, plannedFinish);
    long slipDays = Math.max(0, ChronoUnit.DAYS.between(plannedFinish, scheduledFinish));
    double ratio = Math.min(1.0, (double) slipDays / plannedDuration);
    return new DeadlineSlip(ratio, slipDays, plannedFinish);
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

  private RiskLevel determineRiskLevel(double healthScore) {
    if (healthScore >= 80) return RiskLevel.LOW;
    if (healthScore >= 60) return RiskLevel.MEDIUM;
    if (healthScore >= 40) return RiskLevel.HIGH;
    return RiskLevel.CRITICAL;
  }

  private String serializeFloatDistribution(Map<String, Integer> distribution) {
    try {
      return OBJECT_MAPPER.writeValueAsString(distribution);
    } catch (Exception e) {
      log.error("Error serializing float distribution", e);
      return "{}";
    }
  }
}
