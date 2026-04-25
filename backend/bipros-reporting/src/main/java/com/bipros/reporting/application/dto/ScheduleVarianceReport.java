package com.bipros.reporting.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Primavera P6-style schedule variance report. Compares each baselined activity's frozen
 * dates against the live activity row and reports the deltas with full P6 column coverage
 * (BL Start / Cur Start / Var Start / BL Finish / Cur Finish / Var Finish / Duration variance
 * / float / critical / status / type).
 *
 * <p>Sign convention (matches P6): <b>positive variance days = late, negative = ahead</b>.
 * Variance is {@code current - baseline}.
 */
public record ScheduleVarianceReport(
    ProjectInfo project,
    BaselineInfo baseline,
    LocalDate dataDate,
    Summary summary,
    List<Row> rows
) {

  public record ProjectInfo(UUID id, String code, String name) {}

  public record BaselineInfo(UUID id, String name, LocalDate baselineDate) {}

  public record Summary(
      int totalActivities,
      int slippedCount,        // finish variance > 0
      int aheadCount,          // finish variance < 0
      int onTrackCount,        // finish variance == 0
      int criticalSlippedCount,
      int milestoneSlippedCount,
      double avgStartVarianceDays,
      double avgFinishVarianceDays,
      long worstFinishVarianceDays,
      String worstActivityCode,
      String worstActivityName) {}

  public record Row(
      UUID activityId,
      String code,
      String name,
      String activityType,            // TASK_DEPENDENT / START_MILESTONE / FINISH_MILESTONE / ...
      String status,                  // NOT_STARTED / IN_PROGRESS / COMPLETED / ...
      Double percentComplete,
      LocalDate baselineStart,
      LocalDate currentStart,
      long startVarianceDays,
      LocalDate baselineFinish,
      LocalDate currentFinish,
      long finishVarianceDays,
      Double baselineOriginalDuration,
      Double currentOriginalDuration,
      double durationVarianceDays,
      Double totalFloat,
      Boolean isCritical,
      boolean isMilestone) {}
}
