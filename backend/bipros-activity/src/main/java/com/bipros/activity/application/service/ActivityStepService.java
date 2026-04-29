package com.bipros.activity.application.service;

import com.bipros.activity.application.dto.ActivityStepResponse;
import com.bipros.activity.application.dto.CreateActivityStepRequest;
import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityStatus;
import com.bipros.activity.domain.model.ActivityStep;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.activity.domain.repository.ActivityStepRepository;
import com.bipros.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ActivityStepService {

  private final ActivityStepRepository stepRepository;
  private final ActivityRepository activityRepository;

  public ActivityStepResponse createStep(UUID activityId, CreateActivityStepRequest request) {
    log.info("Creating step for activity: activityId={}, name={}", activityId, request.name());

    // Verify activity exists
    activityRepository.findById(activityId)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", activityId));

    ActivityStep step = new ActivityStep();
    step.setActivityId(activityId);
    step.setName(request.name());
    step.setDescription(request.description());
    step.setWeight(request.weight());
    step.setIsCompleted(false);

    ActivityStep saved = stepRepository.save(step);
    recalculateWeightPercents(activityId);

    log.info("Step created successfully: id={}", saved.getId());
    return ActivityStepResponse.from(saved);
  }

  public ActivityStepResponse updateStep(UUID stepId, String name, String description,
      Double weight) {
    log.info("Updating step: stepId={}", stepId);

    ActivityStep step = stepRepository.findById(stepId)
        .orElseThrow(() -> new ResourceNotFoundException("ActivityStep", stepId));

    step.setName(name);
    step.setDescription(description);
    step.setWeight(weight);

    ActivityStep updated = stepRepository.save(step);
    recalculateWeightPercents(step.getActivityId());
    rollupPercentComplete(step.getActivityId());

    log.info("Step updated successfully: stepId={}", stepId);
    return ActivityStepResponse.from(updated);
  }

  public void deleteStep(UUID stepId) {
    log.info("Deleting step: stepId={}", stepId);

    ActivityStep step = stepRepository.findById(stepId)
        .orElseThrow(() -> new ResourceNotFoundException("ActivityStep", stepId));

    UUID activityId = step.getActivityId();
    stepRepository.deleteById(stepId);
    recalculateWeightPercents(activityId);
    rollupPercentComplete(activityId);

    log.info("Step deleted successfully: stepId={}", stepId);
  }

  public ActivityStepResponse completeStep(UUID stepId) {
    log.info("Completing step: stepId={}", stepId);

    ActivityStep step = stepRepository.findById(stepId)
        .orElseThrow(() -> new ResourceNotFoundException("ActivityStep", stepId));

    step.setIsCompleted(true);
    ActivityStep updated = stepRepository.save(step);
    recalculateWeightPercents(step.getActivityId());
    rollupPercentComplete(step.getActivityId());

    log.info("Step completed successfully: stepId={}", stepId);
    return ActivityStepResponse.from(updated);
  }

  public ActivityStepResponse uncompleteStep(UUID stepId) {
    log.info("Uncompleting step: stepId={}", stepId);

    ActivityStep step = stepRepository.findById(stepId)
        .orElseThrow(() -> new ResourceNotFoundException("ActivityStep", stepId));

    step.setIsCompleted(false);
    ActivityStep updated = stepRepository.save(step);
    recalculateWeightPercents(step.getActivityId());
    rollupPercentComplete(step.getActivityId());

    log.info("Step uncompleted successfully: stepId={}", stepId);
    return ActivityStepResponse.from(updated);
  }

  public List<ActivityStepResponse> getSteps(UUID activityId) {
    log.info("Getting steps for activity: activityId={}", activityId);
    return stepRepository.findByActivityIdOrderBySortOrder(activityId).stream()
        .map(ActivityStepResponse::from)
        .toList();
  }

  public void recalculateWeightPercents(UUID activityId) {
    log.info("Recalculating weight percents for activity: activityId={}", activityId);

    List<ActivityStep> steps = stepRepository.findByActivityIdOrderBySortOrder(activityId);

    if (steps.isEmpty()) {
      return;
    }

    double totalWeight = steps.stream()
        .mapToDouble(ActivityStep::getWeight)
        .sum();

    if (totalWeight <= 0) {
      log.warn("Total weight is zero or negative for activity: activityId={}", activityId);
      return;
    }

    steps.forEach(step -> {
      double percent = (step.getWeight() / totalWeight) * 100.0;
      step.setWeightPercent(percent);
    });

    stepRepository.saveAll(steps);
    log.info("Weight percents recalculated successfully for activity: activityId={}", activityId);
  }

  /**
   * Roll up completed step weights to the parent Activity's percentComplete.
   * Uses the same weighted-step formula as EVM: sum of weightPercent for completed steps.
   * Also derives the activity status from the new percentComplete.
   */
  private void rollupPercentComplete(UUID activityId) {
    List<ActivityStep> steps = stepRepository.findByActivityIdOrderBySortOrder(activityId);
    if (steps.isEmpty()) {
      return;
    }

    double totalWeight = steps.stream().mapToDouble(ActivityStep::getWeight).sum();
    if (totalWeight <= 0) {
      return;
    }

    double completedWeight = steps.stream()
        .filter(s -> Boolean.TRUE.equals(s.getIsCompleted()))
        .mapToDouble(ActivityStep::getWeight)
        .sum();

    double percentComplete = (completedWeight / totalWeight) * 100.0;
    percentComplete = Math.round(percentComplete * 100.0) / 100.0; // round to 2 decimal places

    Activity activity = activityRepository.findById(activityId).orElse(null);
    if (activity == null) {
      return;
    }

    activity.setPercentComplete(percentComplete);

    // Derive status from the new percentComplete
    if (percentComplete >= 100.0) {
      activity.setStatus(ActivityStatus.COMPLETED);
      if (activity.getActualFinishDate() == null) {
        activity.setActualFinishDate(java.time.LocalDate.now());
      }
    } else if (percentComplete > 0.0) {
      activity.setStatus(ActivityStatus.IN_PROGRESS);
      if (activity.getActualStartDate() == null) {
        activity.setActualStartDate(java.time.LocalDate.now());
      }
    }

    activityRepository.save(activity);
    log.info("Rolled up percentComplete={} for activity: activityId={}", percentComplete, activityId);
  }
}
