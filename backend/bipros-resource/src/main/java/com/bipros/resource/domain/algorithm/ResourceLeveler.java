package com.bipros.resource.domain.algorithm;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Heuristic-based resource leveling algorithm.
 * Resolves resource overallocations by delaying lower-priority activities.
 */
public class ResourceLeveler {

  private static final int MAX_ITERATIONS = 1000;
  private static final double EPSILON = 0.0001;

  /**
   * Represents an activity with scheduling dates and priority.
   */
  public record ActivityInfo(
      UUID activityId,
      LocalDate earlyStart,
      LocalDate earlyFinish,
      LocalDate lateStart,
      LocalDate lateFinish,
      Double totalFloat,
      Integer projectPriority,
      LocalDate currentStart,
      LocalDate currentFinish) {

    public long getDuration() {
      return ChronoUnit.DAYS.between(currentStart, currentFinish) + 1;
    }
  }

  /**
   * Represents a resource assignment for an activity.
   */
  public record AssignmentInfo(UUID activityId, UUID resourceId, Double plannedUnits) {}

  /**
   * Input parameters for leveling.
   */
  public record LevelingInput(
      List<ActivityInfo> activities,
      List<AssignmentInfo> assignments,
      Map<UUID, Double> resourceMaxUnits) {}

  /**
   * Result of resource leveling including delays and metrics.
   */
  public record LevelingOutput(
      Map<UUID, LocalDate> delayedActivities,
      List<String> overallocationsResolved,
      int iterationsUsed) {}

  /**
   * Executes the resource leveling algorithm.
   *
   * @param input The leveling input with activities, assignments, and resource capacity
   * @return The leveling output with delayed activities and resolution metrics
   */
  public LevelingOutput level(LevelingInput input) {
    // Make mutable copies to track current state
    Map<UUID, ActivityInfo> activityMap = input.activities().stream()
        .collect(Collectors.toMap(ActivityInfo::activityId, a -> a));
    Map<UUID, LocalDate> delayedActivities = new HashMap<>();
    List<String> overallocationsResolved = new ArrayList<>();

    int iteration = 0;
    boolean hasOverallocations = true;

    while (hasOverallocations && iteration < MAX_ITERATIONS) {
      iteration++;

      // Find earliest and latest project dates
      LocalDate projectStart = activityMap.values().stream()
          .map(ActivityInfo::currentStart)
          .min(LocalDate::compareTo)
          .orElse(LocalDate.now());
      LocalDate projectEnd = activityMap.values().stream()
          .map(ActivityInfo::currentFinish)
          .max(LocalDate::compareTo)
          .orElse(LocalDate.now());

      // Check for overallocations
      Map<LocalDate, Map<UUID, Double>> demandPerDay = calculateDemandPerDay(
          input.assignments(), activityMap, projectStart, projectEnd);

      // Find first overallocation
      OverallocationInfo overallocation = findFirstOverallocation(
          demandPerDay, input.resourceMaxUnits());

      if (overallocation == null) {
        hasOverallocations = false;
        continue;
      }

      // Resolve overallocation by delaying lowest-priority activity
      boolean resolved = resolveOverallocation(
          overallocation, activityMap, input.assignments(),
          input.resourceMaxUnits(), delayedActivities, overallocationsResolved);

      if (!resolved) {
        // Couldn't resolve, stop to prevent infinite loop
        hasOverallocations = false;
      }
    }

    return new LevelingOutput(delayedActivities, overallocationsResolved, iteration);
  }

  /**
   * Calculates daily resource demand across all assignments.
   */
  private Map<LocalDate, Map<UUID, Double>> calculateDemandPerDay(
      List<AssignmentInfo> assignments,
      Map<UUID, ActivityInfo> activityMap,
      LocalDate projectStart,
      LocalDate projectEnd) {

    Map<LocalDate, Map<UUID, Double>> demand = new TreeMap<>();

    for (LocalDate date = projectStart; !date.isAfter(projectEnd); date = date.plusDays(1)) {
      demand.put(date, new HashMap<>());
    }

    for (AssignmentInfo assignment : assignments) {
      ActivityInfo activity = activityMap.get(assignment.activityId());
      if (activity == null) {
        continue;
      }

      LocalDate start = activity.currentStart();
      LocalDate finish = activity.currentFinish();
      long duration = activity.getDuration();

      if (duration <= 0) {
        continue;
      }

      double unitsPerDay = assignment.plannedUnits() / duration;

      for (LocalDate date = start; !date.isAfter(finish); date = date.plusDays(1)) {
        if (!date.isBefore(projectStart) && !date.isAfter(projectEnd)) {
          Map<UUID, Double> dailyDemand = demand.get(date);
          dailyDemand.merge(
              assignment.resourceId(), unitsPerDay, (old, nu) -> old + nu);
        }
      }
    }

    return demand;
  }

  /**
   * Record for tracking overallocation details.
   */
  private record OverallocationInfo(LocalDate date, UUID resourceId, Double demand, Double capacity) {}

  /**
   * Finds the first overallocation in the project.
   */
  private OverallocationInfo findFirstOverallocation(
      Map<LocalDate, Map<UUID, Double>> demandPerDay,
      Map<UUID, Double> resourceMaxUnits) {

    for (Map.Entry<LocalDate, Map<UUID, Double>> dayEntry : demandPerDay.entrySet()) {
      LocalDate date = dayEntry.getKey();
      Map<UUID, Double> dailyDemand = dayEntry.getValue();

      for (Map.Entry<UUID, Double> resourceEntry : dailyDemand.entrySet()) {
        UUID resourceId = resourceEntry.getKey();
        Double demand = resourceEntry.getValue();
        Double capacity = resourceMaxUnits.getOrDefault(resourceId, 8.0);

        if (demand > capacity + EPSILON) {
          return new OverallocationInfo(date, resourceId, demand, capacity);
        }
      }
    }

    return null;
  }

  /**
   * Resolves a single overallocation by delaying the lowest-priority activity.
   */
  private boolean resolveOverallocation(
      OverallocationInfo overallocation,
      Map<UUID, ActivityInfo> activityMap,
      List<AssignmentInfo> assignments,
      Map<UUID, Double> resourceMaxUnits,
      Map<UUID, LocalDate> delayedActivities,
      List<String> overallocationsResolved) {

    // Find all activities assigned to this resource that are active on this day
    Set<UUID> activeActivityIds = new HashSet<>();
    for (AssignmentInfo assignment : assignments) {
      if (!assignment.resourceId().equals(overallocation.resourceId())) {
        continue;
      }

      ActivityInfo activity = activityMap.get(assignment.activityId());
      if (activity == null) {
        continue;
      }

      if (!activity.currentStart().isAfter(overallocation.date())
          && !activity.currentFinish().isBefore(overallocation.date())) {
        activeActivityIds.add(assignment.activityId());
      }
    }

    if (activeActivityIds.isEmpty()) {
      return false;
    }

    // Sort activities by priority: project priority (lower=higher), total float (more float=delay first),
    // activity ID (tiebreaker)
    UUID activityToDelay = activeActivityIds.stream()
        .map(activityMap::get)
        .min(Comparator
            .comparing(ActivityInfo::projectPriority)
            .thenComparing(
                a -> -(a.totalFloat() != null ? a.totalFloat() : 0.0)) // Negate to get descending float
            .thenComparing(ActivityInfo::activityId, Comparator.comparing(UUID::toString)))
        .map(ActivityInfo::activityId)
        .orElse(null);

    if (activityToDelay == null) {
      return false;
    }

    ActivityInfo original = activityMap.get(activityToDelay);
    if (original == null) {
      return false;
    }

    // Try to delay the activity
    LocalDate newStart = findNextAvailableDate(
        original.currentFinish().plusDays(1), activityToDelay, overallocation.resourceId(),
        assignments, activityMap, resourceMaxUnits);

    if (newStart == null) {
      return false;
    }

    // Calculate new finish date
    long duration = original.getDuration();
    LocalDate newFinish = newStart.plusDays(duration - 1);

    // Update activity
    ActivityInfo updated = new ActivityInfo(
        original.activityId(),
        original.earlyStart(),
        original.earlyFinish(),
        original.lateStart(),
        original.lateFinish(),
        original.totalFloat(),
        original.projectPriority(),
        newStart,
        newFinish);

    activityMap.put(activityToDelay, updated);
    delayedActivities.put(activityToDelay, newStart);

    String resolutionMsg = String.format(
        "Delayed activity %s from %s to %s to resolve overallocation of resource %s on %s",
        activityToDelay, original.currentStart(), newStart, overallocation.resourceId(),
        overallocation.date());
    overallocationsResolved.add(resolutionMsg);

    return true;
  }

  /**
   * Finds the next available date for an activity to start with sufficient resource capacity.
   */
  private LocalDate findNextAvailableDate(
      LocalDate startSearchDate,
      UUID activityId,
      UUID resourceId,
      List<AssignmentInfo> assignments,
      Map<UUID, ActivityInfo> activityMap,
      Map<UUID, Double> resourceMaxUnits) {

    ActivityInfo activity = activityMap.get(activityId);
    if (activity == null) {
      return null;
    }

    long duration = activity.getDuration();
    Double assignmentUnits = assignments.stream()
        .filter(a -> a.activityId().equals(activityId) && a.resourceId().equals(resourceId))
        .map(AssignmentInfo::plannedUnits)
        .findFirst()
        .orElse(0.0);

    Double capacity = resourceMaxUnits.getOrDefault(resourceId, 8.0);
    LocalDate searchDate = startSearchDate;

    // Limit search to prevent infinite loops (e.g., 365 days ahead)
    LocalDate searchLimit = startSearchDate.plusDays(365);

    while (searchDate.isBefore(searchLimit)) {
      LocalDate candidateStart = searchDate;
      LocalDate candidateFinish = searchDate.plusDays(duration - 1);

      // Check if resource has capacity for all days of this activity
      boolean canFit = true;
      for (LocalDate checkDate = candidateStart; !checkDate.isAfter(candidateFinish);
           checkDate = checkDate.plusDays(1)) {

        double demand = 0.0;
        for (AssignmentInfo assignment : assignments) {
          if (assignment.resourceId().equals(resourceId)) {
            ActivityInfo a = activityMap.get(assignment.activityId());
            if (a != null && !a.currentStart().isAfter(checkDate)
                && !a.currentFinish().isBefore(checkDate)
                && !a.activityId().equals(activityId)) {
              long aDuration = a.getDuration();
              if (aDuration > 0) {
                demand += assignment.plannedUnits() / aDuration;
              }
            }
          }
        }

        if (demand + assignmentUnits > capacity + EPSILON) {
          canFit = false;
          break;
        }
      }

      if (canFit) {
        return candidateStart;
      }

      searchDate = searchDate.plusDays(1);
    }

    return null;
  }
}
