package com.bipros.resource.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Time-phased resource usage for a project — the data behind the P6-style "Resource Assignments"
 * window. Three nested levels: Resource Type → Resource → Activity. Each level carries a
 * {@code plannedByPeriod} and {@code actualByPeriod} map keyed by {@code "YYYY-MM"} period codes.
 *
 * <p>{@code unit}, {@code plannedByPeriod}, and {@code actualByPeriod} are only populated at the
 * type level when every underlying assignment shares the same productivity unit. For a
 * resource-type row whose children mix Bag, Nos, Cum, Day, etc., all three are {@code null} —
 * the UI renders this as {@code —}, matching the way Primavera P6 shows {@code …} for material
 * totals.
 */
public record ResourceUsageTimePhasedResponse(
    /** Period codes (e.g. "2026-05") in chronological order — column headers for the UI grid. */
    List<String> periods,
    LocalDate fromDate,
    LocalDate toDate,
    List<ResourceTypeUsage> resourceTypes) {

  public record ResourceTypeUsage(
      UUID resourceTypeId,
      String resourceTypeCode,
      String resourceTypeName,
      String unit,
      Map<String, Double> plannedByPeriod,
      Map<String, Double> actualByPeriod,
      List<ResourceUsage> resources) {}

  public record ResourceUsage(
      UUID resourceId,
      String resourceCode,
      String resourceName,
      String unit,
      Map<String, Double> plannedByPeriod,
      Map<String, Double> actualByPeriod,
      List<ActivityUsage> activities) {}

  public record ActivityUsage(
      UUID activityId,
      String activityCode,
      String activityName,
      Map<String, Double> plannedByPeriod,
      Map<String, Double> actualByPeriod) {}
}
