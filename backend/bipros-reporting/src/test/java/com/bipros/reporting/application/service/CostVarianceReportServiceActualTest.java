package com.bipros.reporting.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineActivity;
import com.bipros.baseline.infrastructure.repository.BaselineActivityRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRepository;
import com.bipros.cost.application.dto.CostSummaryDto;
import com.bipros.cost.application.service.CostService;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import com.bipros.project.application.service.DprActualCostLookup;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.reporting.application.dto.CostVarianceReport;
import com.bipros.resource.domain.model.ActivitySubContractorAssignment;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ActivitySubContractorAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostVarianceReportServiceActualTest {

  @Mock ProjectRepository projectRepository;
  @Mock WbsNodeRepository wbsNodeRepository;
  @Mock BaselineRepository baselineRepository;
  @Mock BaselineActivityRepository baselineActivityRepository;
  @Mock ActivityRepository activityRepository;
  @Mock ActivityExpenseRepository activityExpenseRepository;
  @Mock ResourceAssignmentRepository resourceAssignmentRepository;
  @Mock ActivitySubContractorAssignmentRepository activitySubContractorAssignmentRepository;
  @Mock CostService costService;
  @Mock DprActualCostLookup dprActualCostLookup;

  private CostVarianceReportService service;

  @BeforeEach
  void setUp() {
    service = new CostVarianceReportService(
        projectRepository,
        wbsNodeRepository,
        baselineRepository,
        baselineActivityRepository,
        activityRepository,
        activityExpenseRepository,
        resourceAssignmentRepository,
        activitySubContractorAssignmentRepository,
        costService,
        dprActualCostLookup);
  }

  @Test
  void actualColumnUsesExpensePlusDprLedger_excludingResourceAssignment() {
    UUID projectId = UUID.randomUUID();
    UUID baselineId = UUID.randomUUID();
    UUID actId = UUID.randomUUID();

    Project project = new Project();
    project.setId(projectId);
    project.setCode("P1");
    project.setName("Test Project");
    project.setActiveBaselineId(baselineId);

    Baseline baseline = new Baseline();
    baseline.setId(baselineId);
    baseline.setProjectId(projectId);
    baseline.setName("BL1");
    baseline.setBaselineDate(LocalDate.now());

    BaselineActivity ba = new BaselineActivity();
    ba.setBaselineId(baselineId);
    ba.setActivityId(actId);
    ba.setPlannedCost(new BigDecimal("200"));

    Activity activity = new Activity();
    activity.setId(actId);
    activity.setProjectId(projectId);
    activity.setCode("A1");
    activity.setName("Activity 1");
    activity.setPercentComplete(0.0);

    ActivityExpense expense = new ActivityExpense();
    expense.setActivityId(actId);
    expense.setProjectId(projectId);
    expense.setActualCost(new BigDecimal("100"));

    ResourceAssignment ra = ResourceAssignment.builder()
        .activityId(actId)
        .projectId(projectId)
        .actualCost(new BigDecimal("999"))
        .build();

    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(baselineRepository.findById(baselineId)).thenReturn(Optional.of(baseline));
    when(wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId)).thenReturn(List.of());
    when(costService.getCostSummary(projectId)).thenReturn(
        CostSummaryDto.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0));
    when(costService.getEvmByWbs(projectId)).thenReturn(List.of());
    when(baselineActivityRepository.findByBaselineId(baselineId)).thenReturn(List.of(ba));
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(activity));
    when(activityExpenseRepository.findByProjectId(projectId)).thenReturn(List.of(expense));
    when(resourceAssignmentRepository.findByProjectId(projectId)).thenReturn(List.of(ra));
    when(activitySubContractorAssignmentRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(dprActualCostLookup.sumByActivity(projectId)).thenReturn(Map.of(actId, new BigDecimal("40")));

    CostVarianceReport report = service.getReport(projectId, baselineId);
    CostVarianceReport.ActivityRow row = report.activityRows().get(0);
    assertEquals(0, new BigDecimal("140").compareTo(row.actualCost()));  // 100 + 40, RA(999) excluded
  }

  @Test
  void plannedColumnIncludesSubContractorAssignmentAlongsideResourceAssignment() {
    UUID projectId = UUID.randomUUID();
    UUID baselineId = UUID.randomUUID();
    UUID actId = UUID.randomUUID();

    Project project = new Project();
    project.setId(projectId);
    project.setCode("P1");
    project.setName("Test Project");
    project.setActiveBaselineId(baselineId);

    Baseline baseline = new Baseline();
    baseline.setId(baselineId);
    baseline.setProjectId(projectId);
    baseline.setName("BL1");
    baseline.setBaselineDate(LocalDate.now());

    BaselineActivity ba = new BaselineActivity();
    ba.setBaselineId(baselineId);
    ba.setActivityId(actId);
    ba.setPlannedCost(new BigDecimal("200"));

    Activity activity = new Activity();
    activity.setId(actId);
    activity.setProjectId(projectId);
    activity.setCode("A1");
    activity.setName("Activity 1");
    activity.setPercentComplete(0.0);

    ResourceAssignment ra = ResourceAssignment.builder()
        .activityId(actId)
        .projectId(projectId)
        .plannedCost(new BigDecimal("200"))
        .build();

    ActivitySubContractorAssignment sa = ActivitySubContractorAssignment.builder()
        .activityId(actId)
        .projectId(projectId)
        .plannedCost(new BigDecimal("25"))
        .build();

    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(baselineRepository.findById(baselineId)).thenReturn(Optional.of(baseline));
    when(wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId)).thenReturn(List.of());
    when(costService.getCostSummary(projectId)).thenReturn(
        CostSummaryDto.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0));
    when(costService.getEvmByWbs(projectId)).thenReturn(List.of());
    when(baselineActivityRepository.findByBaselineId(baselineId)).thenReturn(List.of(ba));
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(activity));
    when(activityExpenseRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(resourceAssignmentRepository.findByProjectId(projectId)).thenReturn(List.of(ra));
    when(activitySubContractorAssignmentRepository.findByProjectId(projectId)).thenReturn(List.of(sa));
    when(dprActualCostLookup.sumByActivity(projectId)).thenReturn(Map.of());

    CostVarianceReport report = service.getReport(projectId, baselineId);
    CostVarianceReport.ActivityRow row = report.activityRows().get(0);
    assertEquals(0, new BigDecimal("225").compareTo(row.currentPlannedCost()));  // 200 (RA) + 25 (SC)
  }
}
