package com.bipros.baseline.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.baseline.application.dto.BaselineVarianceResponse;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineActivity;
import com.bipros.baseline.infrastructure.repository.BaselineActivityRepository;
import com.bipros.baseline.infrastructure.repository.BaselineExpenseRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRelationshipRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRepository;
import com.bipros.baseline.infrastructure.repository.BaselineResourceAssignmentRepository;
import com.bipros.baseline.infrastructure.repository.BaselineWbsRepository;
import com.bipros.common.util.AuditService;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import com.bipros.project.application.service.DprActualCostLookup;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.resource.domain.repository.ActivitySubContractorAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaselineServiceVarianceActualTest {

  @Mock BaselineRepository baselineRepository;
  @Mock BaselineActivityRepository baselineActivityRepository;
  @Mock BaselineRelationshipRepository baselineRelationshipRepository;
  @Mock BaselineWbsRepository baselineWbsRepository;
  @Mock BaselineResourceAssignmentRepository baselineResourceAssignmentRepository;
  @Mock BaselineExpenseRepository baselineExpenseRepository;
  @Mock ActivityRepository activityRepository;
  @Mock ActivityRelationshipRepository activityRelationshipRepository;
  @Mock ActivityExpenseRepository activityExpenseRepository;
  @Mock ResourceAssignmentRepository resourceAssignmentRepository;
  @Mock ProjectRepository projectRepository;
  @Mock WbsNodeRepository wbsNodeRepository;
  @Mock AuditService auditService;
  @Mock ApplicationEventPublisher eventPublisher;
  @Mock DprActualCostLookup dprActualCostLookup;
  @Mock ActivitySubContractorAssignmentRepository activitySubContractorAssignmentRepository;

  private BaselineService service;

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
  void costVarianceUsesExpensePlusDprLedger_excludingResourceAssignment() {
    UUID projectId = UUID.randomUUID();
    UUID baselineId = UUID.randomUUID();
    UUID actId = UUID.randomUUID();

    Baseline baseline = new Baseline();
    baseline.setId(baselineId);
    baseline.setProjectId(projectId);
    baseline.setName("BL1");
    baseline.setBaselineDate(LocalDate.now());

    BaselineActivity ba = new BaselineActivity();
    ba.setBaselineId(baselineId);
    ba.setActivityId(actId);
    ba.setPlannedCost(new BigDecimal("30"));

    Activity activity = new Activity();
    activity.setId(actId);
    activity.setProjectId(projectId);
    activity.setCode("A1");
    activity.setName("Activity 1");

    ActivityExpense expense = new ActivityExpense();
    expense.setActivityId(actId);
    expense.setProjectId(projectId);
    expense.setActualCost(new BigDecimal("100"));

    when(baselineRepository.findById(baselineId)).thenReturn(Optional.of(baseline));
    when(baselineActivityRepository.findByBaselineId(baselineId)).thenReturn(List.of(ba));
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(activity));
    when(activityExpenseRepository.findByProjectId(projectId)).thenReturn(List.of(expense));
    when(dprActualCostLookup.sumByActivity(projectId)).thenReturn(Map.of(actId, new BigDecimal("40")));

    List<BaselineVarianceResponse> resp = service.getVariance(projectId, baselineId);

    assertEquals(0, new BigDecimal("110").compareTo(resp.get(0).costVariance()));
  }

  @Test
  void comparableTrue_whenBaselineEarlyFinishAndCurrentPlannedFinishBothPresent() {
    UUID projectId = UUID.randomUUID();
    UUID baselineId = UUID.randomUUID();
    UUID actId = UUID.randomUUID();

    Baseline baseline = new Baseline();
    baseline.setId(baselineId);
    baseline.setProjectId(projectId);
    baseline.setName("BL1");
    baseline.setBaselineDate(LocalDate.now());

    BaselineActivity ba = new BaselineActivity();
    ba.setBaselineId(baselineId);
    ba.setActivityId(actId);
    ba.setEarlyFinish(LocalDate.now());

    Activity activity = new Activity();
    activity.setId(actId);
    activity.setProjectId(projectId);
    activity.setCode("A1");
    activity.setName("Activity 1");
    activity.setPlannedFinishDate(LocalDate.now().plusDays(2));

    when(baselineRepository.findById(baselineId)).thenReturn(Optional.of(baseline));
    when(baselineActivityRepository.findByBaselineId(baselineId)).thenReturn(List.of(ba));
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(activity));
    when(activityExpenseRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(dprActualCostLookup.sumByActivity(projectId)).thenReturn(Map.of());

    List<BaselineVarianceResponse> resp = service.getVariance(projectId, baselineId);

    assertEquals(true, resp.get(0).comparable());
  }

  @Test
  void comparableFalse_whenBaselineEarlyFinishMissing() {
    UUID projectId = UUID.randomUUID();
    UUID baselineId = UUID.randomUUID();
    UUID actId = UUID.randomUUID();

    Baseline baseline = new Baseline();
    baseline.setId(baselineId);
    baseline.setProjectId(projectId);
    baseline.setName("BL1");
    baseline.setBaselineDate(LocalDate.now());

    BaselineActivity ba = new BaselineActivity();
    ba.setBaselineId(baselineId);
    ba.setActivityId(actId);
    // earlyFinish intentionally left null

    Activity activity = new Activity();
    activity.setId(actId);
    activity.setProjectId(projectId);
    activity.setCode("A1");
    activity.setName("Activity 1");
    activity.setPlannedFinishDate(LocalDate.now().plusDays(2));

    when(baselineRepository.findById(baselineId)).thenReturn(Optional.of(baseline));
    when(baselineActivityRepository.findByBaselineId(baselineId)).thenReturn(List.of(ba));
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(activity));
    when(activityExpenseRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(dprActualCostLookup.sumByActivity(projectId)).thenReturn(Map.of());

    List<BaselineVarianceResponse> resp = service.getVariance(projectId, baselineId);

    assertEquals(false, resp.get(0).comparable());
  }

  @Test
  void comparableFalse_whenCurrentPlannedFinishMissing() {
    UUID projectId = UUID.randomUUID();
    UUID baselineId = UUID.randomUUID();
    UUID actId = UUID.randomUUID();

    Baseline baseline = new Baseline();
    baseline.setId(baselineId);
    baseline.setProjectId(projectId);
    baseline.setName("BL1");
    baseline.setBaselineDate(LocalDate.now());

    BaselineActivity ba = new BaselineActivity();
    ba.setBaselineId(baselineId);
    ba.setActivityId(actId);
    ba.setEarlyFinish(LocalDate.now());

    Activity activity = new Activity();
    activity.setId(actId);
    activity.setProjectId(projectId);
    activity.setCode("A1");
    activity.setName("Activity 1");
    // plannedFinishDate intentionally left null

    when(baselineRepository.findById(baselineId)).thenReturn(Optional.of(baseline));
    when(baselineActivityRepository.findByBaselineId(baselineId)).thenReturn(List.of(ba));
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(activity));
    when(activityExpenseRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(dprActualCostLookup.sumByActivity(projectId)).thenReturn(Map.of());

    List<BaselineVarianceResponse> resp = service.getVariance(projectId, baselineId);

    assertEquals(false, resp.get(0).comparable());
  }

  @Test
  void finishVarianceReflectsActualFinish_notPlanned_whenActivityComplete() {
    UUID projectId = UUID.randomUUID();
    UUID baselineId = UUID.randomUUID();
    UUID actId = UUID.randomUUID();

    Baseline baseline = new Baseline();
    baseline.setId(baselineId);
    baseline.setProjectId(projectId);
    baseline.setName("BL1");
    baseline.setBaselineDate(LocalDate.now());

    LocalDate baselineFinish = LocalDate.of(2025, 1, 31);
    BaselineActivity ba = new BaselineActivity();
    ba.setBaselineId(baselineId);
    ba.setActivityId(actId);
    ba.setEarlyFinish(baselineFinish);

    Activity activity = new Activity();
    activity.setId(actId);
    activity.setProjectId(projectId);
    activity.setCode("A1");
    activity.setName("Activity 1");
    // Planned finish deliberately equals the baseline — the old (buggy) comparison against
    // plannedFinishDate would read 0 variance. The activity actually completed 10 days late.
    activity.setPlannedFinishDate(baselineFinish);
    activity.setActualFinishDate(baselineFinish.plusDays(10));

    when(baselineRepository.findById(baselineId)).thenReturn(Optional.of(baseline));
    when(baselineActivityRepository.findByBaselineId(baselineId)).thenReturn(List.of(ba));
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(activity));
    when(activityExpenseRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(dprActualCostLookup.sumByActivity(projectId)).thenReturn(Map.of());

    List<BaselineVarianceResponse> resp = service.getVariance(projectId, baselineId);

    assertEquals(10L, resp.get(0).finishVarianceDays());
  }
}
