package com.bipros.importexport.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record ImportPreview(
    int activitiesInFile, int matched, int newActivities, int missingInFile,
    int wbsNodes, int relationships, int resourceAssignments,
    LocalDate dateRangeStart, LocalDate dateRangeFinish, BigDecimal totalPlannedCost,
    List<String> missingActivityCodes, List<String> warnings, ResourceApplyResult resources) {

  /** Copies every field, replacing {@code resources} — used to attach the role-resource preview
   * summary once it's computed separately from the schedule preview. */
  public ImportPreview withResources(ResourceApplyResult r) {
    return new ImportPreview(activitiesInFile, matched, newActivities, missingInFile,
        wbsNodes, relationships, resourceAssignments,
        dateRangeStart, dateRangeFinish, totalPlannedCost,
        missingActivityCodes, warnings, r);
  }

  /** Copies every field, prepending {@code w} to {@code warnings} — used to surface a format-
   * detection notice (e.g. the selected format didn't match the actual file) ahead of any
   * warnings the schedule/resource apply already produced. */
  public ImportPreview withPrependedWarning(String w) {
    List<String> updated = new ArrayList<>();
    updated.add(w);
    if (warnings != null) {
      updated.addAll(warnings);
    }
    return new ImportPreview(activitiesInFile, matched, newActivities, missingInFile,
        wbsNodes, relationships, resourceAssignments,
        dateRangeStart, dateRangeFinish, totalPlannedCost,
        missingActivityCodes, updated, resources);
  }
}
