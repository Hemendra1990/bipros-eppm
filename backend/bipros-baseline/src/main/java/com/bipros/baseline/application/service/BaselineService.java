package com.bipros.baseline.application.service;

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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BaselineService {

  private final BaselineRepository baselineRepository;
  private final BaselineActivityRepository baselineActivityRepository;
  private final BaselineRelationshipRepository baselineRelationshipRepository;

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

    // In a real implementation, fetch current activities from activity service
    // and calculate variance
    return baselineActivities.stream()
        .map(
            activity ->
                new BaselineVarianceResponse(
                    activity.getActivityId(),
                    "Activity " + activity.getActivityId(),
                    0L, // startVarianceDays - would calculate from current activity
                    0L, // finishVarianceDays
                    0.0, // durationVariance
                    BigDecimal.ZERO)) // costVariance
        .toList();
  }
}
