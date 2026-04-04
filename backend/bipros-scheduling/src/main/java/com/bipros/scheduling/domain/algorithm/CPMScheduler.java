package com.bipros.scheduling.domain.algorithm;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.scheduling.domain.model.SchedulingOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class CPMScheduler {

  private final CalendarCalculator calendarCalculator;
  private final UUID defaultCalendarId;

  public List<ScheduledActivity> schedule(ScheduleData data) {
    log.debug(
        "Starting CPM scheduling for project: id={}, dataDate={}, option={}",
        data.projectId(),
        data.dataDate(),
        data.schedulingOption());

    // Build activity index
    Map<UUID, SchedulableActivity> activityMap = new HashMap<>();
    for (SchedulableActivity activity : data.activities()) {
      activityMap.put(activity.id(), activity);
    }

    // Build adjacency lists for predecessors and successors
    Map<UUID, List<SchedulableRelationship>> predecessorMap = new HashMap<>();
    Map<UUID, List<SchedulableRelationship>> successorMap = new HashMap<>();
    Map<UUID, Integer> inDegree = new HashMap<>();

    for (SchedulableActivity activity : data.activities()) {
      predecessorMap.putIfAbsent(activity.id(), new ArrayList<>());
      successorMap.putIfAbsent(activity.id(), new ArrayList<>());
      inDegree.putIfAbsent(activity.id(), 0);
    }

    for (SchedulableRelationship rel : data.relationships()) {
      predecessorMap.computeIfAbsent(rel.successorId(), k -> new ArrayList<>()).add(rel);
      successorMap.computeIfAbsent(rel.predecessorId(), k -> new ArrayList<>()).add(rel);
      inDegree.put(rel.successorId(), inDegree.getOrDefault(rel.successorId(), 0) + 1);
    }

    // Topological sort (Kahn's algorithm) to detect circular dependencies
    List<UUID> topologicalOrder = topologicalSort(data.activities(), inDegree, successorMap);
    if (topologicalOrder.size() != data.activities().size()) {
      throw new BusinessRuleException("CIRCULAR_DEPENDENCY", "Circular dependency detected in activity relationships");
    }

    // Initialize scheduled activities
    Map<UUID, ScheduledActivity> scheduledActivities = new HashMap<>();
    for (SchedulableActivity activity : data.activities()) {
      scheduledActivities.put(activity.id(), new ScheduledActivity(activity.id(), activity.remainingDuration()));
    }

    // Forward pass - calculate early start and early finish
    log.debug("Starting forward pass");
    forwardPass(data, topologicalOrder, activityMap, scheduledActivities, predecessorMap);

    // Backward pass - calculate late start and late finish
    log.debug("Starting backward pass");
    backwardPass(data, topologicalOrder, activityMap, scheduledActivities, successorMap);

    // Calculate floats and mark critical path
    log.debug("Calculating floats and marking critical path");
    for (UUID activityId : data.activities().stream().map(SchedulableActivity::id).toList()) {
      ScheduledActivity scheduled = scheduledActivities.get(activityId);
      double totalFloat = calendarCalculator.countWorkingDays(
          defaultCalendarId,
          scheduled.getEarlyStart(),
          scheduled.getLateStart());
      scheduled.setTotalFloat(totalFloat);
      scheduled.setCritical(totalFloat == 0);
    }

    // Calculate free float for each activity
    for (SchedulableRelationship rel : data.relationships()) {
      ScheduledActivity successor = scheduledActivities.get(rel.successorId());
      ScheduledActivity predecessor = scheduledActivities.get(rel.predecessorId());

      if (rel.type().equals("FS")) {
        double freeFloat = calendarCalculator.countWorkingDays(
            defaultCalendarId,
            predecessor.getEarlyFinish(),
            successor.getEarlyStart());
        successor.setFreeFloat(Math.min(successor.getFreeFloat(), freeFloat));
      }
    }

    log.debug("CPM scheduling completed");
    return new ArrayList<>(scheduledActivities.values());
  }

  private void forwardPass(
      ScheduleData data,
      List<UUID> topologicalOrder,
      Map<UUID, SchedulableActivity> activityMap,
      Map<UUID, ScheduledActivity> scheduledActivities,
      Map<UUID, List<SchedulableRelationship>> predecessorMap) {

    for (UUID activityId : topologicalOrder) {
      SchedulableActivity activity = activityMap.get(activityId);
      ScheduledActivity scheduled = scheduledActivities.get(activityId);
      LocalDate earlyStart = data.projectStartDate();

      // Get contributions from predecessors
      for (SchedulableRelationship rel : predecessorMap.getOrDefault(activityId, List.of())) {
        ScheduledActivity predScheduled = scheduledActivities.get(rel.predecessorId());
        LocalDate contribution = calculateRelationshipContribution(rel, predScheduled, true);
        if (contribution.isAfter(earlyStart)) {
          earlyStart = contribution;
        }
      }

      // Apply primary constraint
      if (activity.primaryConstraintType() != null && activity.primaryConstraintDate() != null) {
        earlyStart = applyPrimaryConstraintForward(
            activity.primaryConstraintType(),
            activity.primaryConstraintDate(),
            activity.remainingDuration(),
            earlyStart);
      }

      // Handle actuals based on scheduling option
      if (data.schedulingOption() == SchedulingOption.RETAINED_LOGIC) {
        if (activity.actualFinishDate() != null) {
          // Activity is complete, use actual dates
          scheduled.setEarlyStart(activity.actualStartDate());
          scheduled.setEarlyFinish(activity.actualFinishDate());
          continue;
        } else if (activity.actualStartDate() != null) {
          // Activity has started, use actual start
          earlyStart = activity.actualStartDate();
        } else if (activity.status() != null && activity.status().equals("In Progress")) {
          // In progress activity starts from data date
          earlyStart = data.dataDate();
        }
      }

      scheduled.setEarlyStart(earlyStart);
      LocalDate earlyFinish = calendarCalculator.addWorkingDays(
          activity.calendarId(),
          earlyStart,
          activity.remainingDuration());
      scheduled.setEarlyFinish(earlyFinish);
    }
  }

  private void backwardPass(
      ScheduleData data,
      List<UUID> topologicalOrder,
      Map<UUID, SchedulableActivity> activityMap,
      Map<UUID, ScheduledActivity> scheduledActivities,
      Map<UUID, List<SchedulableRelationship>> successorMap) {

    LocalDate projectFinishDate = data.mustFinishByDate() != null ? data.mustFinishByDate() : data.projectStartDate();

    // Find the maximum early finish to determine project finish
    for (SchedulableActivity activity : data.activities()) {
      ScheduledActivity scheduled = scheduledActivities.get(activity.id());
      if (scheduled.getEarlyFinish().isAfter(projectFinishDate)) {
        projectFinishDate = scheduled.getEarlyFinish();
      }
    }

    // Backward pass in reverse topological order
    for (int i = topologicalOrder.size() - 1; i >= 0; i--) {
      UUID activityId = topologicalOrder.get(i);
      SchedulableActivity activity = activityMap.get(activityId);
      ScheduledActivity scheduled = scheduledActivities.get(activityId);

      LocalDate lateFinish = projectFinishDate;

      // Get contributions from successors
      for (SchedulableRelationship rel : successorMap.getOrDefault(activityId, List.of())) {
        ScheduledActivity succScheduled = scheduledActivities.get(rel.successorId());
        LocalDate contribution = calculateRelationshipContribution(rel, succScheduled, false);
        if (contribution.isBefore(lateFinish)) {
          lateFinish = contribution;
        }
      }

      // Apply secondary constraint
      if (activity.secondaryConstraintType() != null && activity.secondaryConstraintDate() != null) {
        lateFinish = applySecondaryConstraintBackward(
            activity.secondaryConstraintType(),
            activity.secondaryConstraintDate(),
            activity.remainingDuration(),
            lateFinish);
      }

      // Handle actuals
      if (data.schedulingOption() == SchedulingOption.RETAINED_LOGIC && activity.actualFinishDate() != null) {
        scheduled.setLateStart(activity.actualStartDate());
        scheduled.setLateFinish(activity.actualFinishDate());
        continue;
      }

      scheduled.setLateFinish(lateFinish);
      LocalDate lateStart = calendarCalculator.subtractWorkingDays(
          activity.calendarId(),
          lateFinish,
          activity.remainingDuration());
      scheduled.setLateStart(lateStart);
    }
  }

  private LocalDate calculateRelationshipContribution(
      SchedulableRelationship rel,
      ScheduledActivity related,
      boolean isForward) {

    return switch (rel.type()) {
      case "FS" -> isForward
          ? calendarCalculator.addWorkingDays(defaultCalendarId, related.getEarlyFinish(), rel.lag())
          : calendarCalculator.subtractWorkingDays(defaultCalendarId, related.getLateStart(), rel.lag());
      case "FF" -> isForward
          ? calendarCalculator.addWorkingDays(defaultCalendarId, related.getEarlyFinish(), rel.lag())
          : calendarCalculator.subtractWorkingDays(defaultCalendarId, related.getLateFinish(), rel.lag());
      case "SS" -> isForward
          ? calendarCalculator.addWorkingDays(defaultCalendarId, related.getEarlyStart(), rel.lag())
          : calendarCalculator.subtractWorkingDays(defaultCalendarId, related.getLateStart(), rel.lag());
      case "SF" -> isForward
          ? calendarCalculator.addWorkingDays(defaultCalendarId, related.getEarlyStart(), rel.lag())
          : calendarCalculator.subtractWorkingDays(defaultCalendarId, related.getLateFinish(), rel.lag());
      default -> isForward ? related.getEarlyFinish() : related.getLateStart();
    };
  }

  private LocalDate applyPrimaryConstraintForward(
      String constraintType,
      LocalDate constraintDate,
      double duration,
      LocalDate currentEarlyStart) {

    return switch (constraintType) {
      case "START_ON_OR_AFTER" -> constraintDate.isAfter(currentEarlyStart) ? constraintDate : currentEarlyStart;
      case "START_ON" -> constraintDate;
      case "FINISH_ON_OR_AFTER" -> {
        LocalDate adjustedStart = calendarCalculator.subtractWorkingDays(defaultCalendarId, constraintDate, duration);
        yield adjustedStart.isAfter(currentEarlyStart) ? adjustedStart : currentEarlyStart;
      }
      default -> currentEarlyStart;
    };
  }

  private LocalDate applySecondaryConstraintBackward(
      String constraintType,
      LocalDate constraintDate,
      double duration,
      LocalDate currentLateFinish) {

    return switch (constraintType) {
      case "START_ON_OR_BEFORE" -> constraintDate.isBefore(currentLateFinish)
          ? calendarCalculator.addWorkingDays(defaultCalendarId, constraintDate, duration)
          : currentLateFinish;
      case "FINISH_ON_OR_BEFORE" -> constraintDate.isBefore(currentLateFinish) ? constraintDate : currentLateFinish;
      case "FINISH_ON" -> constraintDate;
      default -> currentLateFinish;
    };
  }

  private List<UUID> topologicalSort(
      List<SchedulableActivity> activities,
      Map<UUID, Integer> inDegree,
      Map<UUID, List<SchedulableRelationship>> successorMap) {
    Map<UUID, Integer> inDegreeCopy = new HashMap<>(inDegree);
    Queue<UUID> queue = new LinkedList<>();

    for (UUID activityId : inDegreeCopy.keySet()) {
      if (inDegreeCopy.get(activityId) == 0) {
        queue.add(activityId);
      }
    }

    List<UUID> sorted = new ArrayList<>();
    while (!queue.isEmpty()) {
      UUID activityId = queue.poll();
      sorted.add(activityId);

      // Process successors and decrease their in-degree
      for (SchedulableRelationship rel : successorMap.getOrDefault(activityId, List.of())) {
        UUID successorId = rel.successorId();
        inDegreeCopy.put(successorId, inDegreeCopy.get(successorId) - 1);
        if (inDegreeCopy.get(successorId) == 0) {
          queue.add(successorId);
        }
      }
    }

    return sorted;
  }
}
