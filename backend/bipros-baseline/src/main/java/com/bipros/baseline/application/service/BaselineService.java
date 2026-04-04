package com.bipros.baseline.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.baseline.application.dto.BaselineActivityResponse;
import com.bipros.baseline.application.dto.BaselineDetailResponse;
import com.bipros.baseline.application.dto.BaselineResponse;
import com.bipros.baseline.application.dto.BaselineVarianceResponse;
import com.bipros.baseline.application.dto.CreateBaselineRequest;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineActivity;
import com.bipros.baseline.domain.BaselineType;
import com.bipros.baseline.infrastructure.repository.BaselineActivityRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRelationshipRepository;
import com.bipros.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BaselineService {

  private final BaselineRepository baselineRepository;
  private final BaselineActivityRepository baselineActivityRepository;
  private final BaselineRelationshipRepository baselineRelationshipRepository;
  private final ActivityRepository activityRepository;

  @Transactional
  public BaselineResponse createBaseline(UUID projectId, CreateBaselineRequest request) {
    // Deactivate any existing baseline of the same type for this project
    List<Baseline> existingBaselines =
        baselineRepository.findByProjectIdAndBaselineType(projectId, request.baselineType());
    for (Baseline baseline : existingBaselines) {
      baseline.setIsActive(false);
      baselineRepository.save(baseline);
    }

    // Create new baseline
    Baseline baseline = new Baseline();
    baseline.setProjectId(projectId);
    baseline.setName(request.name());
    baseline.setDescription(request.description());
    baseline.setBaselineType(request.baselineType());
    baseline.setBaselineDate(LocalDate.now());
    baseline.setIsActive(true);

    // Placeholder values - in real implementation, fetch from activity/project service
    baseline.setTotalActivities(0);
    baseline.setTotalCost(BigDecimal.ZERO);
    baseline.setProjectDuration(0.0);
    baseline.setProjectStartDate(LocalDate.now());
    baseline.setProjectFinishDate(LocalDate.now());

    Baseline saved = baselineRepository.save(baseline);
    return BaselineResponse.from(saved);
  }

  public BaselineDetailResponse getBaseline(UUID baselineId) {
    Baseline baseline =
        baselineRepository
            .findById(baselineId)
            .orElseThrow(() -> new ResourceNotFoundException("Baseline", baselineId));

    List<BaselineActivity> activities = baselineActivityRepository.findByBaselineId(baselineId);
    List<BaselineActivityResponse> activityResponses =
        activities.stream().map(BaselineActivityResponse::from).toList();

    return new BaselineDetailResponse(
        BaselineResponse.from(baseline), activityResponses);
  }

  public List<BaselineResponse> listBaselines(UUID projectId) {
    return baselineRepository.findByProjectId(projectId).stream()
        .map(BaselineResponse::from)
        .toList();
  }

  @Transactional
  public void deleteBaseline(UUID baselineId) {
    Baseline baseline =
        baselineRepository
            .findById(baselineId)
            .orElseThrow(() -> new ResourceNotFoundException("Baseline", baselineId));

    baselineActivityRepository.deleteAll(
        baselineActivityRepository.findByBaselineId(baselineId));
    baselineRelationshipRepository.deleteAll(
        baselineRelationshipRepository.findByBaselineId(baselineId));
    baselineRepository.delete(baseline);
  }

  public List<BaselineVarianceResponse> getVariance(
      UUID projectId, UUID baselineId) {
    Baseline baseline =
        baselineRepository
            .findById(baselineId)
            .orElseThrow(() -> new ResourceNotFoundException("Baseline", baselineId));

    if (!baseline.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("Baseline", baselineId);
    }

    List<BaselineActivity> baselineActivities =
        baselineActivityRepository.findByBaselineId(baselineId);

    // Load current activities and index by ID
    List<Activity> currentActivities = activityRepository.findByProjectId(projectId);
    Map<UUID, Activity> activityMap = currentActivities.stream()
        .collect(Collectors.toMap(Activity::getId, activity -> activity));

    return baselineActivities.stream()
        .map(baselineActivity -> calculateVariance(baselineActivity, activityMap))
        .toList();
  }

  private BaselineVarianceResponse calculateVariance(
      BaselineActivity baselineActivity,
      Map<UUID, Activity> currentActivityMap) {
    Activity currentActivity = currentActivityMap.get(baselineActivity.getActivityId());

    Long startVarianceDays = 0L;
    Long finishVarianceDays = 0L;
    Double durationVariance = 0.0;

    if (currentActivity != null) {
      // Calculate start variance (positive = delayed)
      if (baselineActivity.getEarlyStart() != null && currentActivity.getPlannedStartDate() != null) {
        startVarianceDays = ChronoUnit.DAYS.between(
            baselineActivity.getEarlyStart(),
            currentActivity.getPlannedStartDate());
      }

      // Calculate finish variance (positive = delayed)
      if (baselineActivity.getEarlyFinish() != null && currentActivity.getPlannedFinishDate() != null) {
        finishVarianceDays = ChronoUnit.DAYS.between(
            baselineActivity.getEarlyFinish(),
            currentActivity.getPlannedFinishDate());
      }

      // Calculate duration variance
      if (baselineActivity.getOriginalDuration() != null && currentActivity.getOriginalDuration() != null) {
        durationVariance = currentActivity.getOriginalDuration() - baselineActivity.getOriginalDuration();
      }
    }

    return new BaselineVarianceResponse(
        baselineActivity.getActivityId(),
        "Activity " + baselineActivity.getActivityId(),
        startVarianceDays,
        finishVarianceDays,
        durationVariance,
        BigDecimal.ZERO); // costVariance - no cost data in baseline yet
  }
}
