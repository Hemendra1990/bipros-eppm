package com.bipros.reporting.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityType;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineActivity;
import com.bipros.baseline.infrastructure.repository.BaselineActivityRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRepository;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.reporting.application.dto.ScheduleVarianceReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes the schedule variance report by joining {@link BaselineActivity} ↔ {@link Activity}
 * on {@code activityId} and projecting each pair into a P6-style row.
 *
 * <p>Resolution order for the baseline:
 * <ol>
 *   <li>Explicit {@code baselineId} param if non-null and belonging to the project.</li>
 *   <li>Otherwise {@link Project#getActiveBaselineId()}.</li>
 *   <li>Otherwise 404 — the report is meaningless without a reference.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ScheduleVarianceReportService {

  private final ProjectRepository projectRepository;
  private final BaselineRepository baselineRepository;
  private final BaselineActivityRepository baselineActivityRepository;
  private final ActivityRepository activityRepository;

  public ScheduleVarianceReport getReport(UUID projectId, UUID requestedBaselineId) {
    Project project = projectRepository.findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    UUID baselineId = requestedBaselineId != null
        ? requestedBaselineId
        : project.getActiveBaselineId();
    if (baselineId == null) {
      throw new ResourceNotFoundException("Baseline", "no-active-baseline-for-project-" + projectId);
    }

    Baseline baseline = baselineRepository.findById(baselineId)
        .orElseThrow(() -> new ResourceNotFoundException("Baseline", baselineId));
    if (!baseline.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("Baseline", baselineId);
    }

    List<BaselineActivity> baselineActivities =
        baselineActivityRepository.findByBaselineId(baselineId);
    Map<UUID, Activity> currentByActivityId = activityRepository.findByProjectId(projectId).stream()
        .collect(Collectors.toMap(Activity::getId, a -> a));

    List<ScheduleVarianceReport.Row> rows = baselineActivities.stream()
        .map(ba -> buildRow(ba, currentByActivityId.get(ba.getActivityId())))
        // Worst slippage at the top — operators care about late, not early.
        .sorted(Comparator
            .comparingLong(ScheduleVarianceReport.Row::finishVarianceDays).reversed()
            .thenComparing(ScheduleVarianceReport.Row::code,
                Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();

    ScheduleVarianceReport.Summary summary = buildSummary(rows);

    return new ScheduleVarianceReport(
        new ScheduleVarianceReport.ProjectInfo(project.getId(), project.getCode(), project.getName()),
        new ScheduleVarianceReport.BaselineInfo(baseline.getId(), baseline.getName(), baseline.getBaselineDate()),
        project.getDataDate(),
        summary,
        rows);
  }

  private ScheduleVarianceReport.Row buildRow(BaselineActivity ba, Activity current) {
    long startVar = 0L;
    long finishVar = 0L;
    double durationVar = 0.0;

    String name = current != null ? current.getName() : "Deleted activity";
    String code = current != null ? current.getCode() : "—";
    ActivityType type = current != null ? current.getActivityType() : null;
    String status = current != null && current.getStatus() != null
        ? current.getStatus().name() : "UNKNOWN";
    Double percentComplete = current != null ? current.getPercentComplete() : null;
    Double totalFloat = current != null ? current.getTotalFloat() : null;
    Boolean isCritical = current != null ? current.getIsCritical() : null;

    if (current != null) {
      if (ba.getEarlyStart() != null && current.getPlannedStartDate() != null) {
        startVar = ChronoUnit.DAYS.between(ba.getEarlyStart(), current.getPlannedStartDate());
      }
      if (ba.getEarlyFinish() != null && current.getPlannedFinishDate() != null) {
        finishVar = ChronoUnit.DAYS.between(ba.getEarlyFinish(), current.getPlannedFinishDate());
      }
      if (ba.getOriginalDuration() != null && current.getOriginalDuration() != null) {
        durationVar = current.getOriginalDuration() - ba.getOriginalDuration();
      }
    }

    boolean isMilestone = type == ActivityType.START_MILESTONE
        || type == ActivityType.FINISH_MILESTONE;

    return new ScheduleVarianceReport.Row(
        ba.getActivityId(),
        code,
        name,
        type != null ? type.name() : "UNKNOWN",
        status,
        percentComplete,
        ba.getEarlyStart(),
        current != null ? current.getPlannedStartDate() : null,
        startVar,
        ba.getEarlyFinish(),
        current != null ? current.getPlannedFinishDate() : null,
        finishVar,
        ba.getOriginalDuration(),
        current != null ? current.getOriginalDuration() : null,
        durationVar,
        totalFloat,
        isCritical,
        isMilestone);
  }

  private ScheduleVarianceReport.Summary buildSummary(List<ScheduleVarianceReport.Row> rows) {
    int slipped = 0, ahead = 0, onTrack = 0, criticalSlipped = 0, milestoneSlipped = 0;
    double sumStart = 0, sumFinish = 0;
    long worst = Long.MIN_VALUE;
    String worstCode = null, worstName = null;

    for (ScheduleVarianceReport.Row r : rows) {
      if (r.finishVarianceDays() > 0) {
        slipped++;
        if (Boolean.TRUE.equals(r.isCritical())) criticalSlipped++;
        if (r.isMilestone()) milestoneSlipped++;
      } else if (r.finishVarianceDays() < 0) {
        ahead++;
      } else {
        onTrack++;
      }
      sumStart += r.startVarianceDays();
      sumFinish += r.finishVarianceDays();
      if (r.finishVarianceDays() > worst) {
        worst = r.finishVarianceDays();
        worstCode = r.code();
        worstName = r.name();
      }
    }

    int n = rows.size();
    double avgStart = n > 0 ? sumStart / n : 0.0;
    double avgFinish = n > 0 ? sumFinish / n : 0.0;
    if (n == 0) {
      worst = 0L;
      worstCode = null;
      worstName = null;
    }

    return new ScheduleVarianceReport.Summary(
        n, slipped, ahead, onTrack, criticalSlipped, milestoneSlipped,
        round2(avgStart), round2(avgFinish), worst, worstCode, worstName);
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}
