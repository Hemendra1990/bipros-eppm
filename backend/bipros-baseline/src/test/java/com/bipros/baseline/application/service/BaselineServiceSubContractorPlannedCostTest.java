package com.bipros.baseline.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.baseline.application.dto.BaselineResponse;
import com.bipros.baseline.application.dto.CreateBaselineRequest;
import com.bipros.baseline.application.dto.UpdateBaselineRequest;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineActivity;
import com.bipros.baseline.domain.BaselineType;
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
import com.bipros.resource.domain.model.ActivitySubContractorAssignment;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ActivitySubContractorAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Confirms the sub-contractor plannedCost term (ActivitySubContractorAssignment.plannedCost)
 * is folded into BaselineActivity.plannedCost and Baseline.totalCost during both
 * createBaseline (initial snapshot) and updateBaseline (selective re-snapshot). Before this
 * fix, only ResourceAssignment.plannedCost + ActivityExpense.budgetedCost were counted, so a
 * baseline captured for an activity with only a sub-contractor assignment silently understated
 * its planned cost.
 */
@ExtendWith(MockitoExtension.class)
class BaselineServiceSubContractorPlannedCostTest {

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
  void createBaseline_plannedCostAndTotalCost_includeSubContractorPlannedCost() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();

    Activity activity = new Activity();
    activity.setId(activityId);
    activity.setProjectId(projectId);
    activity.setCode("A1");
    activity.setName("Activity 1");

    ResourceAssignment ra = new ResourceAssignment();
    ra.setActivityId(activityId);
    ra.setPlannedCost(new BigDecimal("100"));

    ActivitySubContractorAssignment sa = new ActivitySubContractorAssignment();
    sa.setActivityId(activityId);
    sa.setPlannedCost(new BigDecimal("25"));

    when(baselineRepository.findByProjectIdAndBaselineType(projectId, BaselineType.PROJECT))
        .thenReturn(List.of());
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(activity));
    when(activityExpenseRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(resourceAssignmentRepository.findByProjectId(projectId)).thenReturn(List.of(ra));
    when(activitySubContractorAssignmentRepository.findByProjectId(projectId))
        .thenReturn(List.of(sa));
    when(baselineRepository.save(any(Baseline.class))).thenAnswer(inv -> {
      Baseline b = inv.getArgument(0);
      b.setId(UUID.randomUUID());
      return b;
    });

    CreateBaselineRequest request =
        new CreateBaselineRequest("BL1", BaselineType.PROJECT, "desc");
    BaselineResponse response = service.createBaseline(projectId, request);

    // Total cost = resource plannedCost(100) + SC plannedCost(25)
    assertEquals(0, new BigDecimal("125").compareTo(response.totalCost()));

    ArgumentCaptor<BaselineActivity> captor = ArgumentCaptor.forClass(BaselineActivity.class);
    org.mockito.Mockito.verify(baselineActivityRepository).save(captor.capture());
    assertEquals(0, new BigDecimal("125").compareTo(captor.getValue().getPlannedCost()));
  }

  @Test
  void updateBaseline_reSnapshotsPlannedCost_includingSubContractorPlannedCost() {
    UUID projectId = UUID.randomUUID();
    UUID baselineId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();

    Baseline baseline = new Baseline();
    baseline.setId(baselineId);
    baseline.setProjectId(projectId);
    baseline.setName("BL1");

    Activity activity = new Activity();
    activity.setId(activityId);
    activity.setProjectId(projectId);
    activity.setCode("A1");
    activity.setName("Activity 1");

    ResourceAssignment ra = new ResourceAssignment();
    ra.setActivityId(activityId);
    ra.setPlannedCost(new BigDecimal("100"));

    ActivitySubContractorAssignment sa = new ActivitySubContractorAssignment();
    sa.setActivityId(activityId);
    sa.setPlannedCost(new BigDecimal("25"));

    when(baselineRepository.findById(baselineId)).thenReturn(java.util.Optional.of(baseline));
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(activity));
    when(baselineActivityRepository.findByBaselineId(baselineId)).thenReturn(List.of());
    when(activityExpenseRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(resourceAssignmentRepository.findByProjectId(projectId)).thenReturn(List.of(ra));
    when(activitySubContractorAssignmentRepository.findByProjectId(projectId))
        .thenReturn(List.of(sa));

    UpdateBaselineRequest request = new UpdateBaselineRequest(
        null, false, false, null, null, null,
        false, false, false, true, false);

    service.updateBaseline(projectId, baselineId, request);

    ArgumentCaptor<BaselineActivity> captor = ArgumentCaptor.forClass(BaselineActivity.class);
    org.mockito.Mockito.verify(baselineActivityRepository).save(captor.capture());
    assertEquals(0, new BigDecimal("125").compareTo(captor.getValue().getPlannedCost()));
  }
}
