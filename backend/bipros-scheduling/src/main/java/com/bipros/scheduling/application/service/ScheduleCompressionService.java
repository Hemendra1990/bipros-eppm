package com.bipros.scheduling.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.ActivityStatus;
import com.bipros.activity.domain.model.RelationshipType;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.scheduling.application.dto.CompressionAnalysisResponse;
import com.bipros.scheduling.application.dto.CompressionRecommendation;
import com.bipros.scheduling.domain.algorithm.ScheduledActivity;
import com.bipros.scheduling.domain.model.CompressionAnalysis;
import com.bipros.scheduling.domain.model.CompressionType;
import com.bipros.scheduling.domain.model.ScheduleActivityResult;
import com.bipros.scheduling.domain.model.ScheduleResult;
import com.bipros.scheduling.domain.repository.CompressionAnalysisRepository;
import com.bipros.scheduling.domain.repository.ScheduleActivityResultRepository;
import com.bipros.scheduling.domain.repository.ScheduleResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ScheduleCompressionService {

  private static final double CRASH_PREMIUM_FACTOR = 0.5;
  private static final int MAX_CRASH_ITERATIONS = 500;

  private final CompressionAnalysisRepository compressionAnalysisRepository;
  private final ScheduleResultRepository scheduleResultRepository;
  private final ScheduleActivityResultRepository scheduleActivityResultRepository;
  private final ActivityRepository activityRepository;
  private final ActivityRelationshipRepository activityRelationshipRepository;
  private final AuditService auditService;
  private final ResourceAssignmentRepository resourceAssignmentRepository;
  private final SchedulingService schedulingService;

  /**
   * Analyze fast-tracking opportunities.
   */
  public CompressionAnalysisResponse analyzeFastTrack(UUID projectId) {
    log.info("Analyzing fast-track opportunities for project: {}", projectId);

    ScheduleResult latestSchedule = scheduleResultRepository
        .findTopByProjectIdOrderByCalculatedAtDesc(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("ScheduleResult", projectId.toString()));

    List<ScheduleActivityResult> scheduleActivities = scheduleActivityResultRepository
        .findByScheduleResultId(latestSchedule.getId());

    List<Activity> activities = activityRepository.findByProjectId(projectId);
    Map<UUID, Activity> activityMap = activities.stream()
        .collect(Collectors.toMap(Activity::getId, a -> a));

    List<ActivityRelationship> relationships = activityRelationshipRepository
        .findByProjectId(projectId);

    List<CompressionRecommendation> recommendations = new ArrayList<>();
    Double originalDuration = latestSchedule.getCriticalPathLength();

    // Identify critical activities (those with zero total float)
    List<UUID> criticalActivityIds = scheduleActivities.stream()
        .filter(sa -> sa.getTotalFloat() != null && sa.getTotalFloat() == 0)
        .map(ScheduleActivityResult::getActivityId)
        .collect(Collectors.toList());

    // For each FS relationship among critical activities, check parallelization potential
    for (ActivityRelationship rel : relationships) {
      if (rel.getRelationshipType() != RelationshipType.FINISH_TO_START) {
        continue;
      }

      if (!criticalActivityIds.contains(rel.getPredecessorActivityId())
          || !criticalActivityIds.contains(rel.getSuccessorActivityId())) {
        continue;
      }

      Activity predecessor = activityMap.get(rel.getPredecessorActivityId());
      Activity successor = activityMap.get(rel.getSuccessorActivityId());

      if (predecessor == null || successor == null) {
        continue;
      }

      Double predDuration = predecessor.getOriginalDuration();
      if (predDuration == null || predDuration <= 0) {
        predDuration = predecessor.getAtCompletionDuration();
      }
      if (predDuration == null || predDuration <= 0) {
        continue;
      }

      Double potentialOverlap = predDuration * 0.5;
      String reason = String.format(
          "Convert FS relationship to SS: allows %s and %s to overlap by ~%.1f days",
          predecessor.getCode(), successor.getCode(), potentialOverlap);

      recommendations.add(new CompressionRecommendation(
          rel.getPredecessorActivityId(),
          predecessor.getCode(),
          predDuration,
          predDuration,
          potentialOverlap,
          null,
          reason
      ));
    }

    Double totalSaved = recommendations.stream()
        .mapToDouble(CompressionRecommendation::durationSaved)
        .sum();
    Double compressedDuration = originalDuration - totalSaved;

    CompressionAnalysis analysis = CompressionAnalysis.builder()
        .projectId(projectId)
        .analysisType(CompressionType.FAST_TRACK)
        .originalDuration(originalDuration)
        .compressedDuration(compressedDuration)
        .durationSaved(totalSaved)
        .additionalCost(null)
        .recommendations(serializeRecommendations(recommendations))
        .build();

    CompressionAnalysis saved = compressionAnalysisRepository.save(analysis);
    auditService.logCreate("CompressionAnalysis", saved.getId(), toResponse(saved, recommendations));
    return toResponse(saved, recommendations);
  }

  /**
   * Analyze crashing opportunities using an iterative, finish-date-based algorithm.
   * Crashes all current finish-driving activities 1 working day at a time, re-evaluates the
   * project finish after each step, and reverts the final non-improving step so the user is
   * never charged for a crash that buys nothing.
   */
  public CompressionAnalysisResponse analyzeCrashing(UUID projectId) {
    log.info("Analyzing crashing opportunities (iterative) for project: {}", projectId);

    List<Activity> activities = activityRepository.findByProjectId(projectId);
    Map<UUID, Activity> activityMap = activities.stream()
        .collect(Collectors.toMap(Activity::getId, a -> a));

    SchedulingService.CpmEvaluator evaluator = schedulingService.newCpmEvaluator(projectId);
    Map<UUID, Double> baseDur = evaluator.baseSchedulingDurations();
    SchedulingService.CpmSimulation base = evaluator.evaluate(Map.of());

    LocalDate originalFinish = base.projectFinish();
    double originalSpan = base.finishSpanWorkingDays();

    // Pre-compute per-activity floor and costPerDay (based on ORIGINAL duration)
    Map<UUID, Double> floor = new HashMap<>();
    Map<UUID, BigDecimal> costPerDay = new HashMap<>();
    for (Activity a : activities) {
      Double origDur = a.getOriginalDuration();
      if (origDur != null && origDur > 0) {
        floor.put(a.getId(), origDur * 0.5);
        BigDecimal budgetedCost = resourceAssignmentRepository.sumBudgetedCostByActivityId(a.getId());
        if (budgetedCost.signum() > 0) {
          BigDecimal daily = budgetedCost
              .divide(BigDecimal.valueOf(origDur), 4, java.math.RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(CRASH_PREMIUM_FACTOR));
          costPerDay.put(a.getId(), daily);
        } else {
          costPerDay.put(a.getId(), BigDecimal.ZERO);
        }
      }
    }

    Map<UUID, Double> overrides = new HashMap<>(baseDur);
    Map<UUID, Double> totalCrashed = new HashMap<>();
    SchedulingService.CpmSimulation current = base;

    for (int i = 0; i < MAX_CRASH_ITERATIONS; i++) {
      // Capture effectively-final reference for use in the lambda below
      final Map<UUID, Double> currentOverrides = overrides;
      // Finish-driving, crashable candidates from the current simulation
      List<Activity> candidates = current.activities().stream()
          .filter(ScheduledActivity::isCritical)
          .map(sa -> activityMap.get(sa.getActivityId()))
          .filter(a -> a != null
              && a.getStatus() != ActivityStatus.COMPLETED
              && a.getOriginalDuration() != null && a.getOriginalDuration() > 0
              && currentOverrides.getOrDefault(a.getId(), 0.0) > floor.getOrDefault(a.getId(), 0.0))
          .collect(Collectors.toList());

      if (candidates.isEmpty()) break;

      // Snapshot before applying this step (to revert if it doesn't improve finish)
      Map<UUID, Double> snapshotOverrides = new HashMap<>(overrides);
      Map<UUID, Double> snapshotCrashed = new HashMap<>(totalCrashed);

      for (Activity a : candidates) {
        double cur = overrides.getOrDefault(a.getId(), 0.0);
        double floorVal = floor.getOrDefault(a.getId(), 0.0);
        double step = (cur - 1.0 >= floorVal) ? 1.0 : (cur - floorVal);
        if (step > 0) {
          overrides.put(a.getId(), cur - step);
          totalCrashed.merge(a.getId(), step, Double::sum);
        }
      }

      SchedulingService.CpmSimulation next = evaluator.evaluate(overrides);
      if (next.finishSpanWorkingDays() >= current.finishSpanWorkingDays()) {
        // No improvement — revert this step and stop
        overrides = snapshotOverrides;
        totalCrashed = snapshotCrashed;
        break;
      }
      current = next;
    }

    LocalDate compressedFinish = current.projectFinish();
    double compressedSpan = current.finishSpanWorkingDays();
    double durationSaved = Math.max(0.0, originalSpan - compressedSpan);

    List<CompressionRecommendation> recommendations = new ArrayList<>();
    BigDecimal totalAdditionalCost = BigDecimal.ZERO;
    for (Map.Entry<UUID, Double> entry : totalCrashed.entrySet()) {
      UUID actId = entry.getKey();
      double crashed = entry.getValue();
      if (crashed <= 0) continue;
      Activity a = activityMap.get(actId);
      if (a == null) continue;
      double origDur = a.getOriginalDuration() != null ? a.getOriginalDuration() : 0.0;
      double newDur = origDur - crashed;
      BigDecimal cpd = costPerDay.getOrDefault(actId, BigDecimal.ZERO);
      BigDecimal addlCost = BigDecimal.valueOf(crashed).multiply(cpd)
          .setScale(2, java.math.RoundingMode.HALF_UP);
      String reason = String.format(
          "Crash %s by %.1f day(s) (to %.1f) at ~%s/day",
          a.getCode(), crashed, newDur, cpd.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
      recommendations.add(new CompressionRecommendation(actId, a.getCode(), origDur, newDur, crashed, addlCost, reason));
      totalAdditionalCost = totalAdditionalCost.add(addlCost);
    }

    CompressionAnalysis analysis = CompressionAnalysis.builder()
        .projectId(projectId)
        .analysisType(CompressionType.CRASH)
        .originalDuration(originalSpan)
        .compressedDuration(compressedSpan)
        .durationSaved(durationSaved)
        .additionalCost(totalAdditionalCost)
        .originalFinishDate(originalFinish)
        .compressedFinishDate(compressedFinish)
        .recommendations(serializeRecommendations(recommendations))
        .build();

    CompressionAnalysis saved = compressionAnalysisRepository.save(analysis);
    auditService.logCreate("CompressionAnalysis", saved.getId(), toResponse(saved, recommendations));
    return toResponse(saved, recommendations);
  }

  private String serializeRecommendations(List<CompressionRecommendation> recommendations) {
    return recommendations.stream()
        .map(r -> String.format(
            "{\"activityId\":\"%s\",\"code\":\"%s\",\"originalDuration\":%.1f,\"newDuration\":%.1f,\"reason\":\"%s\"}",
            r.activityId(), r.activityCode(), r.originalDuration(), r.newDuration(),
            r.reason().replace("\"", "\\\"")))
        .collect(Collectors.joining(",", "[", "]"));
  }

  private CompressionAnalysisResponse toResponse(CompressionAnalysis analysis,
                                                 List<CompressionRecommendation> recommendations) {
    return new CompressionAnalysisResponse(
        analysis.getId(),
        analysis.getProjectId(),
        analysis.getScenarioId(),
        analysis.getAnalysisType(),
        analysis.getOriginalDuration(),
        analysis.getCompressedDuration(),
        analysis.getDurationSaved(),
        analysis.getAdditionalCost(),
        recommendations,
        analysis.getCreatedAt(),
        analysis.getUpdatedAt(),
        analysis.getOriginalFinishDate(),
        analysis.getCompressedFinishDate()
    );
  }
}
