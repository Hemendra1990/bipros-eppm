package com.bipros.evm.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import com.bipros.evm.application.dto.EvmCalculationResponse;
import com.bipros.evm.application.dto.WbsEvmNode;
import com.bipros.evm.domain.algorithm.EvmTechniqueFactory;
import com.bipros.evm.domain.algorithm.EvmTechniqueStrategy;
import com.bipros.evm.domain.entity.EtcMethod;
import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.evm.domain.entity.EvmTechnique;
import com.bipros.evm.domain.repository.EvmCalculationRepository;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvmRollupService {

    private final ActivityRepository activityRepository;
    private final ActivityExpenseRepository activityExpenseRepository;
    private final ResourceAssignmentRepository resourceAssignmentRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final EvmCalculationRepository evmCalculationRepository;

    private static final int SCALE = 4;

    @Transactional
    public List<WbsEvmNode> calculateWbsTree(UUID projectId, EvmTechnique technique, EtcMethod etcMethod) {
        LocalDate dataDate = LocalDate.now();
        List<WbsNode> allWbs = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId);
        List<Activity> allActivities = activityRepository.findByProjectId(projectId);
        List<ActivityExpense> allExpenses = activityExpenseRepository.findByProjectId(projectId);
        List<ResourceAssignment> allAssignments = resourceAssignmentRepository.findByProjectId(projectId);

        // Group activities by WBS node
        Map<UUID, List<Activity>> activitiesByWbs = allActivities.stream()
                .filter(a -> a.getWbsNodeId() != null)
                .collect(Collectors.groupingBy(Activity::getWbsNodeId));

        // Group expenses by activity
        Map<UUID, List<ActivityExpense>> expensesByActivity = allExpenses.stream()
                .filter(e -> e.getActivityId() != null)
                .collect(Collectors.groupingBy(ActivityExpense::getActivityId));

        // Group assignments by activity
        Map<UUID, List<ResourceAssignment>> assignmentsByActivity = allAssignments.stream()
                .collect(Collectors.groupingBy(ResourceAssignment::getActivityId));

        EvmTechniqueStrategy strategy = EvmTechniqueFactory.getStrategy(technique);

        // Build WBS hierarchy map
        Map<UUID, List<WbsNode>> childrenMap = allWbs.stream()
                .filter(w -> w.getParentId() != null)
                .collect(Collectors.groupingBy(WbsNode::getParentId));

        List<WbsNode> roots = allWbs.stream()
                .filter(w -> w.getParentId() == null)
                .toList();

        // Calculate leaf-level EVM and roll up
        List<WbsEvmNode> result = new ArrayList<>();
        for (WbsNode root : roots) {
            result.add(buildWbsEvmTree(root, childrenMap, activitiesByWbs,
                    expensesByActivity, assignmentsByActivity, strategy, dataDate, etcMethod,
                    projectId));
        }
        return result;
    }

    private WbsEvmNode buildWbsEvmTree(
            WbsNode node,
            Map<UUID, List<WbsNode>> childrenMap,
            Map<UUID, List<Activity>> activitiesByWbs,
            Map<UUID, List<ActivityExpense>> expensesByActivity,
            Map<UUID, List<ResourceAssignment>> assignmentsByActivity,
            EvmTechniqueStrategy strategy,
            LocalDate dataDate,
            EtcMethod etcMethod,
            UUID projectId) {

        List<WbsNode> children = childrenMap.getOrDefault(node.getId(), List.of());

        if (children.isEmpty()) {
            // Leaf node — calculate from activities
            return calculateLeafEvm(node, activitiesByWbs, expensesByActivity,
                    assignmentsByActivity, strategy, dataDate, etcMethod, projectId);
        }

        // Parent node — aggregate children
        List<WbsEvmNode> childResults = new ArrayList<>();
        BigDecimal totalPv = BigDecimal.ZERO;
        BigDecimal totalEv = BigDecimal.ZERO;
        BigDecimal totalAc = BigDecimal.ZERO;
        BigDecimal totalBac = BigDecimal.ZERO;

        for (WbsNode child : children) {
            WbsEvmNode childResult = buildWbsEvmTree(child, childrenMap, activitiesByWbs,
                    expensesByActivity, assignmentsByActivity, strategy, dataDate, etcMethod, projectId);
            childResults.add(childResult);
            totalPv = totalPv.add(childResult.plannedValue());
            totalEv = totalEv.add(childResult.earnedValue());
            totalAc = totalAc.add(childResult.actualCost());
            totalBac = totalBac.add(childResult.budgetAtCompletion());
        }

        // Also include activities directly under this WBS node
        WbsEvmNode directActivities = calculateLeafEvm(node, activitiesByWbs, expensesByActivity,
                assignmentsByActivity, strategy, dataDate, etcMethod, projectId);
        totalPv = totalPv.add(directActivities.plannedValue());
        totalEv = totalEv.add(directActivities.earnedValue());
        totalAc = totalAc.add(directActivities.actualCost());
        totalBac = totalBac.add(directActivities.budgetAtCompletion());

        // Save WBS-level calculation
        EvmCalculation calc = createCalculation(projectId, node.getId(), dataDate,
                totalPv, totalEv, totalAc, totalBac, etcMethod,
                EvmTechniqueFactory.getStrategy(EvmTechnique.ACTIVITY_PERCENT_COMPLETE) == strategy
                        ? EvmTechnique.ACTIVITY_PERCENT_COMPLETE : null);

        return new WbsEvmNode(
                node.getId(),
                node.getName(),
                node.getCode(),
                totalBac, totalPv, totalEv, totalAc,
                calc.getScheduleVariance(), calc.getCostVariance(),
                calc.getSchedulePerformanceIndex(), calc.getCostPerformanceIndex(),
                calc.getEstimateAtCompletion(), calc.getEstimateToComplete(),
                calc.getVarianceAtCompletion(),
                childResults
        );
    }

    private WbsEvmNode calculateLeafEvm(
            WbsNode node,
            Map<UUID, List<Activity>> activitiesByWbs,
            Map<UUID, List<ActivityExpense>> expensesByActivity,
            Map<UUID, List<ResourceAssignment>> assignmentsByActivity,
            EvmTechniqueStrategy strategy,
            LocalDate dataDate,
            EtcMethod etcMethod,
            UUID projectId) {

        List<Activity> activities = activitiesByWbs.getOrDefault(node.getId(), List.of());

        BigDecimal totalPv = BigDecimal.ZERO;
        BigDecimal totalEv = BigDecimal.ZERO;
        BigDecimal totalAc = BigDecimal.ZERO;
        BigDecimal totalBac = BigDecimal.ZERO;

        for (Activity activity : activities) {
            BigDecimal activityBac = getActivityBac(activity, expensesByActivity, assignmentsByActivity);
            BigDecimal activityPv = getActivityPv(activity, activityBac, dataDate);
            BigDecimal activityEv = strategy.calculateEarnedValue(activity, activityBac, activityPv);
            BigDecimal activityAc = getActivityAc(activity, expensesByActivity, assignmentsByActivity);

            totalBac = totalBac.add(activityBac);
            totalPv = totalPv.add(activityPv);
            totalEv = totalEv.add(activityEv);
            totalAc = totalAc.add(activityAc);
        }

        EvmCalculation calc = createCalculation(projectId, node.getId(), dataDate,
                totalPv, totalEv, totalAc, totalBac, etcMethod, null);

        return new WbsEvmNode(
                node.getId(),
                node.getName(),
                node.getCode(),
                totalBac, totalPv, totalEv, totalAc,
                calc.getScheduleVariance(), calc.getCostVariance(),
                calc.getSchedulePerformanceIndex(), calc.getCostPerformanceIndex(),
                calc.getEstimateAtCompletion(), calc.getEstimateToComplete(),
                calc.getVarianceAtCompletion(),
                List.of()
        );
    }

    static BigDecimal getActivityBac(Activity activity,
                                      Map<UUID, List<ActivityExpense>> expensesByActivity,
                                      Map<UUID, List<ResourceAssignment>> assignmentsByActivity) {
        BigDecimal bac = BigDecimal.ZERO;
        List<ActivityExpense> expenses = expensesByActivity.getOrDefault(activity.getId(), List.of());
        for (ActivityExpense expense : expenses) {
            if (expense.getBudgetedCost() != null) {
                bac = bac.add(expense.getBudgetedCost());
            }
        }
        List<ResourceAssignment> assignments = assignmentsByActivity.getOrDefault(activity.getId(), List.of());
        for (ResourceAssignment assignment : assignments) {
            if (assignment.getPlannedCost() != null) {
                bac = bac.add(assignment.getPlannedCost());
            }
        }
        return bac;
    }

    static BigDecimal getActivityPv(Activity activity, BigDecimal activityBac, LocalDate dataDate) {
        if (activity.getPlannedFinishDate() == null) {
            return BigDecimal.ZERO;
        }
        // If planned finish <= data date, full BAC is planned value
        if (!activity.getPlannedFinishDate().isAfter(dataDate)) {
            return activityBac;
        }
        // If planned start <= data date but finish > data date, time-phase proportionally
        if (activity.getPlannedStartDate() != null && !activity.getPlannedStartDate().isAfter(dataDate)) {
            long totalDays = java.time.temporal.ChronoUnit.DAYS.between(
                    activity.getPlannedStartDate(), activity.getPlannedFinishDate());
            if (totalDays <= 0) return activityBac;
            long elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(
                    activity.getPlannedStartDate(), dataDate);
            return activityBac.multiply(BigDecimal.valueOf(elapsedDays))
                    .divide(BigDecimal.valueOf(totalDays), SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    static BigDecimal getActivityAc(Activity activity,
                                     Map<UUID, List<ActivityExpense>> expensesByActivity,
                                     Map<UUID, List<ResourceAssignment>> assignmentsByActivity) {
        BigDecimal ac = BigDecimal.ZERO;
        List<ActivityExpense> expenses = expensesByActivity.getOrDefault(activity.getId(), List.of());
        for (ActivityExpense expense : expenses) {
            if (expense.getActualCost() != null) {
                ac = ac.add(expense.getActualCost());
            }
        }
        List<ResourceAssignment> assignments = assignmentsByActivity.getOrDefault(activity.getId(), List.of());
        for (ResourceAssignment assignment : assignments) {
            if (assignment.getActualCost() != null) {
                ac = ac.add(assignment.getActualCost());
            }
        }
        return ac;
    }

    private EvmCalculation createCalculation(UUID projectId, UUID wbsNodeId, LocalDate dataDate,
                                              BigDecimal pv, BigDecimal ev, BigDecimal ac, BigDecimal bac,
                                              EtcMethod etcMethod, EvmTechnique technique) {
        var calc = new EvmCalculation();
        calc.setProjectId(projectId);
        calc.setWbsNodeId(wbsNodeId);
        calc.setDataDate(dataDate);
        calc.setBudgetAtCompletion(bac);
        calc.setPlannedValue(pv);
        calc.setEarnedValue(ev);
        calc.setActualCost(ac);
        if (technique != null) calc.setEvmTechnique(technique);
        if (etcMethod != null) calc.setEtcMethod(etcMethod);

        EvmServiceHelper.calculateIndices(calc);

        evmCalculationRepository.save(calc);
        return calc;
    }
}
