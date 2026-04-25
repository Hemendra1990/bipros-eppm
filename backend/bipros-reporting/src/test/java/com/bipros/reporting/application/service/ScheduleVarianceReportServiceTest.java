package com.bipros.reporting.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleVarianceReportService")
class ScheduleVarianceReportServiceTest {

  @Mock private ProjectRepository projectRepository;
  @Mock private BaselineRepository baselineRepository;
  @Mock private BaselineActivityRepository baselineActivityRepository;
  @Mock private ActivityRepository activityRepository;

  private ScheduleVarianceReportService service;

  private final UUID projectId = UUID.randomUUID();
  private final UUID baselineId = UUID.randomUUID();
  private final UUID slippedActId = UUID.randomUUID();
  private final UUID onTrackActId = UUID.randomUUID();
  private final UUID aheadActId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new ScheduleVarianceReportService(
        projectRepository,
        baselineRepository,
        baselineActivityRepository,
        activityRepository);
  }

  @Test
  @DisplayName("falls back to active baseline when none specified")
  void fallsBackToActiveBaseline() {
    Project project = newProject(baselineId);
    Baseline baseline = newBaseline(baselineId, projectId);

    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(baselineRepository.findById(baselineId)).thenReturn(Optional.of(baseline));
    when(baselineActivityRepository.findByBaselineId(baselineId)).thenReturn(List.of());
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of());

    ScheduleVarianceReport report = service.getReport(projectId, null);
    assertEquals(baselineId, report.baseline().id());
    assertEquals(0, report.summary().totalActivities());
  }

  @Test
  @DisplayName("404 when no baseline available at all")
  void rejectsMissingBaseline() {
    Project project = newProject(null);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    assertThrows(ResourceNotFoundException.class, () -> service.getReport(projectId, null));
  }

  @Test
  @DisplayName("computes start/finish/duration variance + summary across mixed rows")
  void computesVariance() {
    Project project = newProject(baselineId);
    Baseline baseline = newBaseline(baselineId, projectId);

    BaselineActivity baSlip = newBaselineActivity(slippedActId,
        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), 30.0);
    BaselineActivity baOnTrack = newBaselineActivity(onTrackActId,
        LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), 27.0);
    BaselineActivity baAhead = newBaselineActivity(aheadActId,
        LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31), 30.0);

    Activity slipped = newActivity(slippedActId, "A001", "Slipped",
        LocalDate.of(2025, 1, 11), LocalDate.of(2025, 2, 14), 35.0,
        ActivityType.TASK_DEPENDENT, ActivityStatus.IN_PROGRESS, true);
    Activity onTrack = newActivity(onTrackActId, "A002", "On track",
        LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), 27.0,
        ActivityType.TASK_DEPENDENT, ActivityStatus.NOT_STARTED, false);
    Activity ahead = newActivity(aheadActId, "A003", "Ahead milestone",
        LocalDate.of(2025, 2, 26), LocalDate.of(2025, 3, 26), 28.0,
        ActivityType.FINISH_MILESTONE, ActivityStatus.NOT_STARTED, false);

    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(baselineRepository.findById(baselineId)).thenReturn(Optional.of(baseline));
    when(baselineActivityRepository.findByBaselineId(baselineId))
        .thenReturn(List.of(baSlip, baOnTrack, baAhead));
    when(activityRepository.findByProjectId(projectId))
        .thenReturn(List.of(slipped, onTrack, ahead));

    ScheduleVarianceReport report = service.getReport(projectId, baselineId);

    assertEquals(3, report.summary().totalActivities());
    assertEquals(1, report.summary().slippedCount(), "1 row should be slipped");
    assertEquals(1, report.summary().onTrackCount());
    assertEquals(1, report.summary().aheadCount());
    assertEquals(1, report.summary().criticalSlippedCount());
    assertEquals(0, report.summary().milestoneSlippedCount());

    // Worst slippage = the slipped row, +14 days finish variance.
    assertEquals(14L, report.summary().worstFinishVarianceDays());
    assertEquals("A001", report.summary().worstActivityCode());

    // Sorted worst-first.
    ScheduleVarianceReport.Row first = report.rows().get(0);
    assertEquals("A001", first.code());
    assertEquals(10L, first.startVarianceDays());
    assertEquals(14L, first.finishVarianceDays());
    assertEquals(5.0, first.durationVarianceDays(), 0.001);
    assertTrue(first.isCritical());

    // Milestone row carries the isMilestone flag.
    ScheduleVarianceReport.Row milestoneRow = report.rows().stream()
        .filter(r -> "A003".equals(r.code()))
        .findFirst().orElseThrow();
    assertTrue(milestoneRow.isMilestone());
  }

  // ── builders ──────────────────────────────────────────────────────────

  private Project newProject(UUID activeBaselineId) {
    Project p = new Project();
    p.setId(projectId);
    p.setCode("TEST");
    p.setName("Test project");
    p.setActiveBaselineId(activeBaselineId);
    return p;
  }

  private Baseline newBaseline(UUID id, UUID projId) {
    Baseline b = new Baseline();
    b.setId(id);
    b.setProjectId(projId);
    b.setName("BL1");
    b.setBaselineDate(LocalDate.of(2025, 1, 1));
    return b;
  }

  private BaselineActivity newBaselineActivity(UUID activityId, LocalDate start, LocalDate finish, Double duration) {
    BaselineActivity ba = new BaselineActivity();
    ba.setActivityId(activityId);
    ba.setEarlyStart(start);
    ba.setEarlyFinish(finish);
    ba.setOriginalDuration(duration);
    return ba;
  }

  private Activity newActivity(UUID id, String code, String name,
                               LocalDate plannedStart, LocalDate plannedFinish, Double duration,
                               ActivityType type, ActivityStatus status, boolean critical) {
    Activity a = new Activity();
    a.setId(id);
    a.setCode(code);
    a.setName(name);
    a.setPlannedStartDate(plannedStart);
    a.setPlannedFinishDate(plannedFinish);
    a.setOriginalDuration(duration);
    a.setActivityType(type);
    a.setStatus(status);
    a.setIsCritical(critical);
    return a;
  }
}
