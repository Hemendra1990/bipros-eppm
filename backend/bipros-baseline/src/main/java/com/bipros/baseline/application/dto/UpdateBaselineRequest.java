package com.bipros.baseline.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 4.2: Selective Update Baseline filter spec, modelled on P6's "Update Baseline" dialog.
 * The service re-snapshots only the activities matching the filter and only the requested
 * field categories on those activities.
 *
 * <p>Defaults are deliberately permissive — when the planner clicks "Update" with the dialog
 * untouched, every activity gets refreshed across every field category. Setting any narrowing
 * filter (e.g. {@code criticalOnly = true}) restricts the scope.
 */
public record UpdateBaselineRequest(
    /** When non-empty, only these activity IDs are refreshed. {@code null} or empty = all. */
    List<UUID> activityIds,
    /** P6 "Critical activities only" checkbox. Combines with other filters via AND. */
    Boolean criticalOnly,
    /** "Milestones only" — restricts to {@code activityType in (START_MILESTONE, FINISH_MILESTONE)}. */
    Boolean milestonesOnly,
    /** Restrict by current activity status. {@code null} or empty = no filter. */
    Set<String> statuses,
    /** Activities whose planned-start falls in [from, to] (inclusive). Either bound can be null. */
    LocalDate plannedStartFrom,
    LocalDate plannedStartTo,
    /** Field-category toggles. Default-true via the {@code orDefault} helpers below. */
    Boolean updateDates,
    Boolean updateDurations,
    Boolean updateRelationships,
    Boolean updateResourceCosts,
    Boolean updateExpenseCosts) {

  public boolean dates()         { return orDefault(updateDates, true); }
  public boolean durations()     { return orDefault(updateDurations, true); }
  public boolean relationships() { return orDefault(updateRelationships, true); }
  public boolean resourceCosts() { return orDefault(updateResourceCosts, true); }
  public boolean expenseCosts()  { return orDefault(updateExpenseCosts, true); }

  private static boolean orDefault(Boolean value, boolean fallback) {
    return value == null ? fallback : value;
  }
}
