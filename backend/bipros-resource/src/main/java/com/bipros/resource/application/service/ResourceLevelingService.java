package com.bipros.resource.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.domain.algorithm.LevelingResult;
import com.bipros.resource.domain.algorithm.ResourceLeveler;
import com.bipros.resource.domain.algorithm.ResourceLeveler.ActivityInfo;
import com.bipros.resource.domain.algorithm.ResourceLeveler.AssignmentInfo;
import com.bipros.resource.domain.algorithm.ResourceLeveler.LevelingInput;
import com.bipros.resource.domain.algorithm.ResourceLeveler.LevelingOutput;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for resource leveling operations.
 * Orchestrates resource leveling by loading data, running the algorithm, and updating dates.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ResourceLevelingService {

  private final ActivityRepository activityRepository;
  private final ResourceAssignmentRepository assignmentRepository;
  private final ResourceRepository resourceRepository;
  private final ProjectRepository projectRepository;

  /**
   * Performs resource leveling for all activities in a project.
   *
   * @param projectId The project ID
   * @return Leveling result with delays and metrics
   */
  public LevelingResult levelResources(UUID projectId) {
    log.info("Starting resource leveling for project: {}", projectId);

    // Load project
    Project project = projectRepository.findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    // Load activities for the project
    List<Activity> activities = activityRepository.findByProjectId(projectId);
    if (activities.isEmpty()) {
      log.warn("No activities found for project: {}", projectId);
      return new LevelingResult(Map.of(), List.of(), 0);
    }

    // Load resource assignments for the project
    List<ResourceAssignment> assignments = assignmentRepository.findByProjectId(projectId);

    // Build resource capacity map
    Set<UUID> resourceIds = assignments.stream()
        .map(ResourceAssignment::getResourceId)
        .collect(Collectors.toSet());

    Map<UUID, Double> resourceMaxUnits = new HashMap<>();
    for (UUID resourceId : resourceIds) {
      Resource resource = resourceRepository.findById(resourceId)
          .orElseThrow(() -> new ResourceNotFoundException("Resource", resourceId));
      resourceMaxUnits.put(resourceId, resource.getMaxUnitsPerDay());
    }

    // Convert to algorithm input format
    List<ActivityInfo> activityInfos = activities.stream()
        .map(a -> new ActivityInfo(
            a.getId(),
            a.getEarlyStartDate(),
            a.getEarlyFinishDate(),
            a.getLateStartDate(),
            a.getLateFinishDate(),
            a.getTotalFloat(),
            project.getPriority(),
            a.getPlannedStartDate(),
            a.getPlannedFinishDate()))
        .filter(a -> a.currentStart() != null && a.currentFinish() != null)
        .collect(Collectors.toList());

    List<AssignmentInfo> assignmentInfos = assignments.stream()
        .map(a -> new AssignmentInfo(a.getActivityId(), a.getResourceId(), a.getPlannedUnits()))
        .filter(a -> a.plannedUnits() != null && a.plannedUnits() > 0)
        .collect(Collectors.toList());

    if (activityInfos.isEmpty() || assignmentInfos.isEmpty()) {
      log.warn("Insufficient activity or assignment data for leveling: project={}", projectId);
      return new LevelingResult(Map.of(), List.of(), 0);
    }

    // Run the leveling algorithm
    ResourceLeveler leveler = new ResourceLeveler();
    LevelingInput input = new LevelingInput(activityInfos, assignmentInfos, resourceMaxUnits);
    LevelingOutput output = leveler.level(input);

    log.info("Leveling completed: iterations={}, overallocations resolved={}",
        output.iterationsUsed(), output.overallocationsResolved().size());

    // Update activity dates with delays
    for (Map.Entry<UUID, LocalDate> delay : output.delayedActivities().entrySet()) {
      UUID activityId = delay.getKey();
      LocalDate newStartDate = delay.getValue();

      Activity activity = activityRepository.findById(activityId)
          .orElseThrow(() -> new ResourceNotFoundException("Activity", activityId));

      if (activity.getPlannedStartDate() != null) {
        long originalDuration = java.time.temporal.ChronoUnit.DAYS.between(
            activity.getPlannedStartDate(), activity.getPlannedFinishDate()) + 1;
        LocalDate newFinishDate = newStartDate.plusDays(originalDuration - 1);

        activity.setPlannedStartDate(newStartDate);
        activity.setPlannedFinishDate(newFinishDate);

        activityRepository.save(activity);

        log.info("Updated activity {} dates: start={}, finish={}",
            activityId, newStartDate, newFinishDate);
      }
    }

    // Convert output to result
    LevelingResult result = new LevelingResult(
        output.delayedActivities(),
        output.overallocationsResolved(),
        output.iterationsUsed());

    log.info("Resource leveling completed successfully for project: {}", projectId);
    return result;
  }
}
