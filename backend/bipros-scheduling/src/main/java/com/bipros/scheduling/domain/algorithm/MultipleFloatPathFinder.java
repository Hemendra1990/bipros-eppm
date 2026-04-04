package com.bipros.scheduling.domain.algorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MultipleFloatPathFinder {

  public List<FloatPath> findFloatPaths(
      List<ScheduledActivity> activities,
      Map<UUID, List<SchedulableRelationship>> adjacency) {

    List<FloatPath> paths = new ArrayList<>();
    Set<UUID> processed = new HashSet<>();

    // Find all activities with lowest float
    double minFloat = activities.stream()
        .mapToDouble(ScheduledActivity::getTotalFloat)
        .min()
        .orElse(0);

    int pathNumber = 1;
    for (ScheduledActivity activity : activities) {
      if (!processed.contains(activity.getActivityId()) && activity.getTotalFloat() <= minFloat + 1) {
        FloatPath path = tracePathBackward(activity.getActivityId(), adjacency, activities, processed);
        if (!path.activities().isEmpty()) {
          paths.add(new FloatPath(pathNumber++, path.activities(), path.totalFloat()));
        }
      }
    }

    return paths;
  }

  private FloatPath tracePathBackward(
      UUID startActivityId,
      Map<UUID, List<SchedulableRelationship>> adjacency,
      List<ScheduledActivity> activities,
      Set<UUID> processed) {

    List<UUID> pathActivities = new ArrayList<>();
    Set<UUID> visited = new HashSet<>();
    double totalFloat = findTotalFloat(startActivityId, activities);

    traceBack(startActivityId, adjacency, visited, pathActivities);

    for (UUID actId : pathActivities) {
      processed.add(actId);
    }

    return new FloatPath(0, pathActivities, totalFloat);
  }

  private void traceBack(
      UUID activityId,
      Map<UUID, List<SchedulableRelationship>> adjacency,
      Set<UUID> visited,
      List<UUID> path) {

    if (visited.contains(activityId)) {
      return;
    }
    visited.add(activityId);
    path.add(0, activityId);

    // Find predecessors and trace back
    for (Map.Entry<UUID, List<SchedulableRelationship>> entry : adjacency.entrySet()) {
      for (SchedulableRelationship rel : entry.getValue()) {
        if (rel.successorId().equals(activityId)) {
          traceBack(rel.predecessorId(), adjacency, visited, path);
        }
      }
    }
  }

  private double findTotalFloat(UUID activityId, List<ScheduledActivity> activities) {
    return activities.stream()
        .filter(a -> a.getActivityId().equals(activityId))
        .mapToDouble(ScheduledActivity::getTotalFloat)
        .findFirst()
        .orElse(0);
  }

  public record FloatPath(int pathNumber, List<UUID> activities, double totalFloat) {
  }
}
