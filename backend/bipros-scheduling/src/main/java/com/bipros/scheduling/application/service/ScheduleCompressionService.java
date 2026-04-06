package com.bipros.scheduling.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.RelationshipType;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.scheduling.application.dto.CompressionAnalysisResponse;
import com.bipros.scheduling.application.dto.CompressionRecommendation;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ScheduleCompressionService {

  private final CompressionAnalysisRepository compressionAnalysisRepository;
  private final ScheduleResultRepository scheduleResultRepository;
  private final ScheduleActivityResultRepository scheduleActivityResultRepository;
  private final ActivityRepository activityRepository;
  private final ActivityRelationshipRepository activityRelationshipRepository;
  private final AuditService auditService;

  /**
   * Analyze fast-tracking opportunities.
   * Fast-tracking identifies activities on/near the critical path with FS relationships
   * that could be converted to SS (start-start) to allow parallel execution.
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

      // Estimate overlap as 50% of predecessor duration (conservative estimate)
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
          predDuration, // Duration itself doesn't change in fast-tracking
          potentialOverlap,
          null,
          reason
      ));
    }

    // Calculate total potential duration savings
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
   * Analyze crashing opportunities.
   * Crashing identifies critical activities that can have duration reduced by adding resources.
   * Each activity can be reduced by max 50% of original duration.
   * Estimated crash cost = activity cost * 0.5 / crashable days
   */
  public CompressionAnalysisResponse analyzeCrashing(UUID projectId) {
    log.info("Analyzing crashing opportunities for project: {}", projectId);

    ScheduleResult latestSchedule = scheduleResultRepository
        .findTopByProjectIdOrderByCalculatedAtDesc(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("ScheduleResult", projectId.toString()));

    List<ScheduleActivityResult> scheduleActivities = scheduleActivityResultRepository
        .findByScheduleResultId(latestSchedule.getId());

    List<Activity> activities = activityRepository.findByProjectId(projectId);
    Map<UUID, Activity> activityMap = activities.stream()
        .collect(Collectors.toMap(Activity::getId, a -> a));

    List<CompressionRecommendation> recommendations = new ArrayList<>();
    Double originalDuration = latestSchedule.getCriticalPathLength();
    BigDecimal totalAdditionalCost = BigDecimal.ZERO;

    // Identify critical activities
    List<ScheduleActivityResult> criticalActivities = scheduleActivities.stream()
        .filter(sa -> sa.getTotalFloat() != null && sa.getTotalFloat() == 0)
        .collect(Collectors.toList());

    // Sort by original duration descending (largest first)
    criticalActivities = criticalActivities.stream()
        .map(sa -> {
          Activity a = activityMap.get(sa.getActivityId());
          return new Object[]{sa, a != null ? (a.getOriginalDuration() != null ? a.getOriginalDuration() : 0.0) : 0.0};
        })
        .sorted((a, b) -> Double.compare((Double)b[1], (Double)a[1]))
        .map(o -> (ScheduleActivityResult)o[0])
        .collect(Collectors.toList());

    for (ScheduleActivityResult sa : criticalActivities) {
      Activity activity = activityMap.get(sa.getActivityId());
      if (activity == null) {
        continue;
      }

      Double originalDur = activity.getOriginalDuration();
      if (originalDur == null || originalDur <= 0) {
        originalDur = activity.getAtCompletionDuration();
      }
      if (originalDur == null || originalDur <= 0) {
        continue;
      }

      // Max crash: 50% reduction
      Double maxCrashableDays = originalDur * 0.5;
      Double crashedDuration = originalDur - maxCrashableDays;

      // Estimate crash cost (placeholder: 50% cost per 50% duration reduction)
      BigDecimal estimatedCostPerDay = BigDecimal.valueOf(originalDur * 10); // arbitrary base
      BigDecimal crashCost = estimatedCostPerDay.multiply(BigDecimal.valueOf(maxCrashableDays));

      String reason = String.format(
          "Crash %s by adding resources; max reduction: %.1f days at ~%.2f per day",
          activity.getCode(), maxCrashableDays, 
          crashCost.divide(BigDecimal.valueOf(maxCrashableDays), 2, java.math.RoundingMode.HALF_UP));

      recommendations.add(new CompressionRecommendation(
          activity.getId(),
          activity.getCode(),
          originalDur,
          crashedDuration,
          maxCrashableDays,
          crashCost,
          reason
      ));

      totalAdditionalCost = totalAdditionalCost.add(crashCost);
    }

    // For simplicity, assume crashing the top activities saves 30% of project duration
    Double totalPotentialSavings = originalDuration * 0.30;
    Double compressedDuration = originalDuration - totalPotentialSavings;

    CompressionAnalysis analysis = CompressionAnalysis.builder()
        .projectId(projectId)
        .analysisType(CompressionType.CRASH)
        .originalDuration(originalDuration)
        .compressedDuration(compressedDuration)
        .durationSaved(totalPotentialSavings)
        .additionalCost(totalAdditionalCost)
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
        analysis.getUpdatedAt()
    );
  }
}
