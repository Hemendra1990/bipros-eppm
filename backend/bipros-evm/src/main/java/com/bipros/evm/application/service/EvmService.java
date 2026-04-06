package com.bipros.evm.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import com.bipros.evm.application.dto.CalculateEvmRequest;
import com.bipros.evm.application.dto.EvmCalculationResponse;
import com.bipros.evm.application.dto.EvmSummaryResponse;
import com.bipros.evm.domain.algorithm.EvmTechniqueFactory;
import com.bipros.evm.domain.algorithm.EvmTechniqueStrategy;
import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.evm.domain.entity.EtcMethod;
import com.bipros.evm.domain.entity.EvmTechnique;
import com.bipros.evm.domain.repository.EvmCalculationRepository;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvmService {

    private final EvmCalculationRepository evmCalculationRepository;
    private final ActivityRepository activityRepository;
    private final ActivityExpenseRepository activityExpenseRepository;
    private final ResourceAssignmentRepository resourceAssignmentRepository;
    private final AuditService auditService;

    @Transactional
    public EvmCalculationResponse calculateEvm(UUID projectId, CalculateEvmRequest request) {
        LocalDate dataDate = LocalDate.now();

        List<Activity> activities = activityRepository.findByProjectId(projectId);
        List<ActivityExpense> allExpenses = activityExpenseRepository.findByProjectId(projectId);
        List<ResourceAssignment> allAssignments = resourceAssignmentRepository.findByProjectId(projectId);

        // Group cost data by activity
        Map<UUID, List<ActivityExpense>> expensesByActivity = allExpenses.stream()
                .filter(e -> e.getActivityId() != null)
                .collect(Collectors.groupingBy(ActivityExpense::getActivityId));
        Map<UUID, List<ResourceAssignment>> assignmentsByActivity = allAssignments.stream()
                .collect(Collectors.groupingBy(ResourceAssignment::getActivityId));

        EvmTechniqueStrategy strategy = EvmTechniqueFactory.getStrategy(request.technique());

        BigDecimal totalPv = BigDecimal.ZERO;
        BigDecimal totalEv = BigDecimal.ZERO;
        BigDecimal totalAc = BigDecimal.ZERO;
        BigDecimal totalBac = BigDecimal.ZERO;

        for (Activity activity : activities) {
            BigDecimal activityBac = EvmRollupService.getActivityBac(activity, expensesByActivity, assignmentsByActivity);
            BigDecimal activityPv = EvmRollupService.getActivityPv(activity, activityBac, dataDate);
            BigDecimal activityEv = strategy.calculateEarnedValue(activity, activityBac, activityPv);
            BigDecimal activityAc = EvmRollupService.getActivityAc(activity, expensesByActivity, assignmentsByActivity);

            totalBac = totalBac.add(activityBac);
            totalPv = totalPv.add(activityPv);
            totalEv = totalEv.add(activityEv);
            totalAc = totalAc.add(activityAc);
        }

        var calculation = new EvmCalculation();
        calculation.setProjectId(projectId);
        calculation.setDataDate(dataDate);
        calculation.setEvmTechnique(request.technique());
        calculation.setEtcMethod(request.etcMethod());
        calculation.setBudgetAtCompletion(totalBac);
        calculation.setPlannedValue(totalPv);
        calculation.setEarnedValue(totalEv);
        calculation.setActualCost(totalAc);

        EvmServiceHelper.calculateIndices(calculation);

        var saved = evmCalculationRepository.save(calculation);
        auditService.logCreate("EvmCalculation", saved.getId(), EvmCalculationResponse.from(saved));
        return EvmCalculationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public EvmCalculationResponse getLatestEvm(UUID projectId) {
        return evmCalculationRepository.findTopByProjectIdOrderByDataDateDesc(projectId)
                .map(EvmCalculationResponse::from)
                .orElse(emptyEvmResponse(projectId));
    }

    private EvmCalculationResponse emptyEvmResponse(UUID projectId) {
        return new EvmCalculationResponse(
                null, projectId, null, null, null, LocalDate.now(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0.0, 0.0, 0.0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, 0.0);
    }

    @Transactional(readOnly = true)
    public List<EvmCalculationResponse> getEvmHistory(UUID projectId) {
        return evmCalculationRepository.findByProjectIdOrderByDataDateDesc(projectId)
                .stream()
                .map(EvmCalculationResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EvmCalculationResponse getEvmByWbs(UUID projectId, UUID wbsNodeId) {
        var entity = evmCalculationRepository.findTopByProjectIdAndWbsNodeIdOrderByDataDateDesc(projectId, wbsNodeId)
                .orElseThrow(() -> new ResourceNotFoundException("EvmCalculation", wbsNodeId));
        return EvmCalculationResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public EvmSummaryResponse getSummary(UUID projectId) {
        var optCalc = evmCalculationRepository.findTopByProjectIdOrderByDataDateDesc(projectId);
        if (optCalc.isEmpty()) {
            return new EvmSummaryResponse(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0.0, 0.0, 0.0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    null, null, 0.0);
        }
        var calculation = optCalc.get();

        return new EvmSummaryResponse(
                calculation.getBudgetAtCompletion(),
                calculation.getPlannedValue(),
                calculation.getEarnedValue(),
                calculation.getActualCost(),
                calculation.getScheduleVariance(),
                calculation.getCostVariance(),
                calculation.getSchedulePerformanceIndex(),
                calculation.getCostPerformanceIndex(),
                calculation.getToCompletePerformanceIndex(),
                calculation.getEstimateAtCompletion(),
                calculation.getEstimateToComplete(),
                calculation.getVarianceAtCompletion(),
                calculation.getEvmTechnique() != null ? calculation.getEvmTechnique().toString() : null,
                calculation.getEtcMethod() != null ? calculation.getEtcMethod().toString() : null,
                calculation.getPerformancePercentComplete()
        );
    }
}
