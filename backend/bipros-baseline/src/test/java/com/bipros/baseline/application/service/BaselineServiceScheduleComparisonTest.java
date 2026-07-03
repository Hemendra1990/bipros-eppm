package com.bipros.baseline.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.baseline.application.dto.ScheduleComparisonResponse;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineActivity;
import com.bipros.baseline.infrastructure.repository.BaselineActivityRepository;
import com.bipros.baseline.infrastructure.repository.BaselineExpenseRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRelationshipRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRepository;
import com.bipros.baseline.infrastructure.repository.BaselineResourceAssignmentRepository;
import com.bipros.baseline.infrastructure.repository.BaselineWbsRepository;
import com.bipros.common.util.AuditService;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import com.bipros.project.application.service.DprActualCostLookup;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.resource.domain.repository.ActivitySubContractorAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BaselineService.getScheduleComparison")
class BaselineServiceScheduleComparisonTest {

  @Mock private BaselineRepository baselineRepository;
  @Mock private BaselineActivityRepository baselineActivityRepository;
  @Mock private BaselineRelationshipRepository baselineRelationshipRepository;
  @Mock private BaselineWbsRepository baselineWbsRepository;
  @Mock private BaselineResourceAssignmentRepository baselineResourceAssignmentRepository;
  @Mock private BaselineExpenseRepository baselineExpenseRepository;
  @Mock private ActivityRepository activityRepository;
  @Mock private ActivityRelationshipRepository activityRelationshipRepository;
  @Mock private ActivityExpenseRepository activityExpenseRepository;
  @Mock private ResourceAssignmentRepository resourceAssignmentRepository;
  @Mock private ProjectRepository projectRepository;
  @Mock private WbsNodeRepository wbsNodeRepository;
  @Mock private AuditService auditService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private DprActualCostLookup dprActualCostLookup;
  @Mock private ActivitySubContractorAssignmentRepository activitySubContractorAssignmentRepository;

  private BaselineService service;

  private final UUID projectId = UUID.randomUUID();
  private final UUID baselineId = UUID.randomUUID();
  private final UUID activityId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new BaselineService(
        baselineRepository,
        baselineActivityRepository,
        baselineRelationshipRepository,
        baselineWbsRepository,
        baselineResourceAssignmentRepository,
        baselineExpenseRepository,
        activityRepository,
        activityRelationshipRepository,
        activityExpenseRepository,
        resourceAssignmentRepository,
        projectRepository,
        wbsNodeRepository,
        auditService,
        eventPublisher,
        dprActualCostLookup,
        activitySubContractorAssignmentRepository);
  }

  @Test
  @DisplayName("all-null dates classify as NOT_COMPARABLE with null variances")
  void allNullDates_classifyNotComparable_withNullVariances() {
    Baseline baseline = new Baseline();
    baseline.setId(baselineId);
    baseline.setProjectId(projectId);

    BaselineActivity ba = new BaselineActivity();
    ba.setActivityId(activityId);
    ba.setEarlyStart(null);
    ba.setEarlyFinish(null);

    Activity activity = new Activity();
    activity.setId(activityId);
    activity.setName("Undated activity");
    activity.setPlannedStartDate(null);
    activity.setPlannedFinishDate(null);

    when(baselineRepository.findById(baselineId)).thenReturn(Optional.of(baseline));
    when(baselineActivityRepository.findByBaselineId(baselineId)).thenReturn(List.of(ba));
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(activity));

    List<ScheduleComparisonResponse> rows = service.getScheduleComparison(projectId, baselineId);

    ScheduleComparisonResponse r = rows.get(0);
    assertEquals(ScheduleComparisonResponse.ComparisonStatus.NOT_COMPARABLE, r.status());
    assertNull(r.startVarianceDays());
    assertNull(r.finishVarianceDays());
  }

  @Test
  @DisplayName("actual finish differing from baseline classifies as CHANGED, not UNCHANGED")
  void actualFinishDiffersFromBaseline_classifiesChanged() {
    Baseline baseline = new Baseline();
    baseline.setId(baselineId);
    baseline.setProjectId(projectId);

    LocalDate baselineStart = LocalDate.of(2025, 1, 1);
    LocalDate baselineFinish = LocalDate.of(2025, 1, 31);

    BaselineActivity ba = new BaselineActivity();
    ba.setActivityId(activityId);
    ba.setEarlyStart(baselineStart);
    ba.setEarlyFinish(baselineFinish);

    Activity activity = new Activity();
    activity.setId(activityId);
    activity.setName("Activity");
    // Planned dates deliberately equal the baseline; the old comparison (planned vs baseline)
    // would read UNCHANGED here. The activity actually finished 5 days later than baseline.
    activity.setPlannedStartDate(baselineStart);
    activity.setPlannedFinishDate(baselineFinish);
    activity.setActualStartDate(baselineStart);
    activity.setActualFinishDate(baselineFinish.plusDays(5));

    when(baselineRepository.findById(baselineId)).thenReturn(Optional.of(baseline));
    when(baselineActivityRepository.findByBaselineId(baselineId)).thenReturn(List.of(ba));
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(activity));

    List<ScheduleComparisonResponse> rows = service.getScheduleComparison(projectId, baselineId);

    ScheduleComparisonResponse r = rows.get(0);
    assertEquals(ScheduleComparisonResponse.ComparisonStatus.CHANGED, r.status());
    assertEquals(5L, r.finishVarianceDays());
  }
}
