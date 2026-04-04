package com.bipros.resource.domain.algorithm;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Result of resource leveling operation.
 *
 * @param delayedActivities Map of activity ID to new start date for delayed activities
 * @param overallocationsResolved List of overallocation resolution messages
 * @param iterations Number of iterations performed by the leveling algorithm
 */
public record LevelingResult(
    Map<UUID, LocalDate> delayedActivities,
    List<String> overallocationsResolved,
    int iterations) {}
