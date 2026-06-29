package com.bipros.reporting.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineActivity;
import com.bipros.baseline.infrastructure.repository.BaselineActivityRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRepository;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.cost.application.dto.CostSummaryDto;
import com.bipros.cost.application.dto.WbsEvmRow;
import com.bipros.cost.application.service.CostService;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.reporting.application.dto.CostVarianceReport;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cost-variance counterpart to {@link ScheduleVarianceReportService}. Pulls EVM rollups
 * (project + WBS) from the latest {@code EvmCalculation} per node, then layers the
 * baseline-vs-live activity cost grid on top.
 */
@Service
@RequiredArgsConstructor
public class CostVarianceReportService {

  private final ProjectRepository projectRepository;
  private final WbsNodeRepository wbsNodeRepository;
  private final BaselineRepository baselineRepository;
  private final BaselineActivityRepository baselineActivityRepository;
  private final ActivityRepository activityRepository;
  private final ActivityExpenseRepository activityExpenseRepository;
  private final ResourceAssignmentRepository resourceAssignmentRepository;
  private final CostService costService;

  public CostVarianceReport getReport(UUID projectId, UUID requestedBaselineId) {
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

    // Build project-level EVM summary from the canonical cost service.
    CostSummaryDto cs = costService.getCostSummary(projectId);
    CostVarianceReport.Summary summary = toSummary(cs);

    // Per-WBS EVM rows from the canonical cost service.
    Map<String, WbsNode> nodeByCode = new HashMap<>();
    for (WbsNode n : wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId)) {
      if (n.getCode() != null) nodeByCode.put(n.getCode(), n);
    }
    List<WbsEvmRow> evmByWbs = costService.getEvmByWbs(projectId);
    List<CostVarianceReport.WbsRow> wbsRows = evmByWbs.stream()
        .map(row -> toWbsRow(row, nodeByCode.get(row.code())))
        .sorted(Comparator.comparing(CostVarianceReport.WbsRow::wbsCode,
            Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();

    // Per-activity baseline-cost variance.
    List<BaselineActivity> baselineActivities = baselineActivityRepository.findByBaselineId(baselineId);
    Map<UUID, Activity> currentByActivityId = activityRepository.findByProjectId(projectId).stream()
        .collect(Collectors.toMap(Activity::getId, a -> a));

    Map<UUID, List<ActivityExpense>> expensesByActivity = activityExpenseRepository
        .findByProjectId(projectId).stream()
        .filter(e -> e.getActivityId() != null)
        .collect(Collectors.groupingBy(ActivityExpense::getActivityId));
    Map<UUID, List<ResourceAssignment>> assignmentsByActivity = resourceAssignmentRepository
        .findByProjectId(projectId).stream()
        .collect(Collectors.groupingBy(ResourceAssignment::getActivityId));

    List<CostVarianceReport.ActivityRow> activityRows = baselineActivities.stream()
        .map(ba -> buildActivityRow(ba,
            currentByActivityId.get(ba.getActivityId()),
            expensesByActivity,
            assignmentsByActivity))
        // Worst burn variance first.
        .sorted(Comparator.comparing(CostVarianceReport.ActivityRow::burnVariance,
            Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();

    return new CostVarianceReport(
        new CostVarianceReport.ProjectInfo(project.getId(), project.getCode(), project.getName()),
        new CostVarianceReport.BaselineInfo(baseline.getId(), baseline.getName(), baseline.getBaselineDate()),
        project.getDataDate(),
        summary,
        wbsRows,
        activityRows);
  }

  private static CostVarianceReport.Summary toSummary(CostSummaryDto cs) {
    return new CostVarianceReport.Summary(
        cs.bac(),
        cs.plannedValue(),
        cs.earnedValue(),
        cs.totalActual(),
        cs.scheduleVariance(),
        cs.costVariance(),
        cs.schedulePerformanceIndex() != null ? cs.schedulePerformanceIndex().doubleValue() : null,
        cs.costPerformanceIndex() != null ? cs.costPerformanceIndex().doubleValue() : null,
        cs.estimateAtCompletion(),
        cs.varianceAtCompletion(),
        cs.costPercentComplete() != null ? cs.costPercentComplete().multiply(BigDecimal.valueOf(100)).doubleValue() : null);
  }

  private static CostVarianceReport.WbsRow toWbsRow(WbsEvmRow row, WbsNode node) {
    return new CostVarianceReport.WbsRow(
        node != null ? node.getId() : null,
        row.code(),
        row.name(),
        node != null ? node.getWbsLevel() : null,
        row.bac(),
        row.plannedValue(),
        row.earnedValue(),
        row.actualCost(),
        row.costVariance(),
        row.costPerformanceIndex() != null ? row.costPerformanceIndex().doubleValue() : null);
  }

  private CostVarianceReport.ActivityRow buildActivityRow(
      BaselineActivity ba,
      Activity current,
      Map<UUID, List<ActivityExpense>> expensesByActivity,
      Map<UUID, List<ResourceAssignment>> assignmentsByActivity) {

    String code = current != null ? current.getCode() : "—";
    String name = current != null ? current.getName() : "Deleted activity";
    String type = current != null && current.getActivityType() != null
        ? current.getActivityType().name() : "UNKNOWN";
    String status = current != null && current.getStatus() != null
        ? current.getStatus().name() : "UNKNOWN";
    Double percentComplete = current != null ? current.getPercentComplete() : null;

    BigDecimal baselinePlanned = ba.getPlannedCost() != null ? ba.getPlannedCost() : BigDecimal.ZERO;
    BigDecimal currentPlanned = sumPlanned(ba.getActivityId(), expensesByActivity, assignmentsByActivity);
    BigDecimal currentActual = sumActual(ba.getActivityId(), expensesByActivity, assignmentsByActivity);

    BigDecimal estimateVariance = currentPlanned.subtract(baselinePlanned);

    // Burn variance compares actual cost against the *expected* baseline burn at this
    // % complete. Falls back to actual − baseline if percentComplete is unknown.
    BigDecimal expectedBurn = percentComplete != null
        ? baselinePlanned.multiply(BigDecimal.valueOf(percentComplete / 100.0))
            .setScale(2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;
    BigDecimal burnVariance = currentActual.subtract(expectedBurn);

    return new CostVarianceReport.ActivityRow(
        ba.getActivityId(),
        code,
        name,
        type,
        status,
        percentComplete,
        baselinePlanned,
        currentPlanned,
        currentActual,
        estimateVariance,
        burnVariance);
  }

  private static BigDecimal sumPlanned(UUID activityId,
                                       Map<UUID, List<ActivityExpense>> expenses,
                                       Map<UUID, List<ResourceAssignment>> assignments) {
    BigDecimal sum = BigDecimal.ZERO;
    List<ActivityExpense> es = expenses.get(activityId);
    if (es != null) {
      for (ActivityExpense e : es) if (e.getBudgetedCost() != null) sum = sum.add(e.getBudgetedCost());
    }
    List<ResourceAssignment> as = assignments.get(activityId);
    if (as != null) {
      for (ResourceAssignment a : as) if (a.getPlannedCost() != null) sum = sum.add(a.getPlannedCost());
    }
    return sum;
  }

  private static BigDecimal sumActual(UUID activityId,
                                      Map<UUID, List<ActivityExpense>> expenses,
                                      Map<UUID, List<ResourceAssignment>> assignments) {
    BigDecimal sum = BigDecimal.ZERO;
    List<ActivityExpense> es = expenses.get(activityId);
    if (es != null) {
      for (ActivityExpense e : es) if (e.getActualCost() != null) sum = sum.add(e.getActualCost());
    }
    List<ResourceAssignment> as = assignments.get(activityId);
    if (as != null) {
      for (ResourceAssignment a : as) if (a.getActualCost() != null) sum = sum.add(a.getActualCost());
    }
    return sum;
  }
}
