package com.bipros.evm.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.activity.domain.model.PercentCompleteType;
import com.bipros.common.event.EvmRecalculatedEvent;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.entity.CostAccount;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import com.bipros.cost.domain.repository.CostAccountRepository;
import com.bipros.evm.application.dto.ActivityEvmResponse;
import com.bipros.evm.application.dto.CalculateEvmRequest;
import com.bipros.evm.application.dto.CostAccountRollupResponse;
import com.bipros.evm.application.dto.EvmCalculationResponse;
import com.bipros.evm.application.dto.EvmSummaryResponse;
import com.bipros.evm.domain.algorithm.EvmTechniqueFactory;
import com.bipros.evm.domain.algorithm.EvmTechniqueStrategy;
import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.evm.domain.entity.EtcMethod;
import com.bipros.evm.domain.entity.EvmTechnique;
import com.bipros.evm.domain.repository.EvmCalculationRepository;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvmService {

    private static final int SCALE = 4;

    private final EvmCalculationRepository evmCalculationRepository;
    private final ActivityRepository activityRepository;
    private final ActivityExpenseRepository activityExpenseRepository;
    private final ResourceAssignmentRepository resourceAssignmentRepository;
    private final CostAccountRepository costAccountRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

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
        eventPublisher.publishEvent(new EvmRecalculatedEvent(
            saved.getProjectId(), saved.getId(), saved.getDataDate()));
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
    public ActivityEvmResponse getActivityEvm(UUID projectId, UUID activityId) {
        var activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", activityId));

        LocalDate dataDate = LocalDate.now();

        List<ActivityExpense> expenses = activityExpenseRepository.findByActivityId(activityId);
        List<ResourceAssignment> assignments = resourceAssignmentRepository.findByActivityId(activityId);

        Map<UUID, List<ActivityExpense>> expensesByActivity = expenses.stream()
                .collect(Collectors.groupingBy(e -> activityId));
        Map<UUID, List<ResourceAssignment>> assignmentsByActivity = assignments.stream()
                .collect(Collectors.groupingBy(r -> activityId));

        BigDecimal bac = EvmRollupService.getActivityBac(activity, expensesByActivity, assignmentsByActivity);
        BigDecimal pv = EvmRollupService.getActivityPv(activity, bac, dataDate);

        EvmTechnique technique = resolveEvmTechnique(activity.getPercentCompleteType());
        EvmTechniqueStrategy strategy = EvmTechniqueFactory.getStrategy(technique);
        BigDecimal ev = strategy.calculateEarnedValue(activity, bac, pv);

        BigDecimal ac = EvmRollupService.getActivityAc(activity, expensesByActivity, assignmentsByActivity);

        BigDecimal cv = ev.subtract(ac);
        BigDecimal sv = ev.subtract(pv);

        Double cpi = ac.compareTo(BigDecimal.ZERO) != 0
                ? ev.divide(ac, 4, RoundingMode.HALF_UP).doubleValue()
                : null;
        Double spi = pv.compareTo(BigDecimal.ZERO) != 0
                ? ev.divide(pv, 4, RoundingMode.HALF_UP).doubleValue()
                : null;

        Double pct = activity.getPercentComplete();

        return new ActivityEvmResponse(
                activityId, projectId,
                bac, pv, ev, ac, cv, sv,
                cpi, spi,
                pct,
                technique.name()
        );
    }

    private static EvmTechnique resolveEvmTechnique(PercentCompleteType type) {
        if (type == null) return EvmTechnique.ACTIVITY_PERCENT_COMPLETE;
        return switch (type) {
            case PHYSICAL -> EvmTechnique.WEIGHTED_STEPS;
            case DURATION, UNITS -> EvmTechnique.ACTIVITY_PERCENT_COMPLETE;
        };
    }

    /**
     * Rolls up EVM metrics (BAC, PV, EV, AC, CV, SV, CPI, SPI) per cost account for the given
     * project, using P6-style soft inheritance: activity.costAccountId wins; if null, falls back
     * to the WBS node's costAccountId; if still null, the activity lands in the "Unassigned"
     * bucket.
     *
     * <p>PV/SV/SPI are null for a bucket when any contributing activity has a null PV (partial
     * data). CPI is null when the bucket's AC is zero.
     *
     * <p>Sorting: assigned buckets by code ascending, "Unassigned" last.
     */
    @Transactional(readOnly = true)
    public List<CostAccountRollupResponse> getCostAccountRollup(UUID projectId) {
        LocalDate dataDate = LocalDate.now();

        List<Activity> activities = activityRepository.findByProjectId(projectId);
        List<ActivityExpense> allExpenses = activityExpenseRepository.findByProjectId(projectId);
        List<ResourceAssignment> allAssignments = resourceAssignmentRepository.findByProjectId(projectId);

        // Pre-load all WBS nodes for the project to support in-memory inheritance lookup (N+1 safe)
        Map<UUID, WbsNode> wbsById = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId)
                .stream()
                .collect(Collectors.toMap(w -> w.getId(), w -> w));

        // Group cost data by activity
        Map<UUID, List<ActivityExpense>> expensesByActivity = allExpenses.stream()
                .filter(e -> e.getActivityId() != null)
                .collect(Collectors.groupingBy(ActivityExpense::getActivityId));
        Map<UUID, List<ResourceAssignment>> assignmentsByActivity = allAssignments.stream()
                .collect(Collectors.groupingBy(ResourceAssignment::getActivityId));

        // Accumulator per resolved cost account ID (null = unassigned)
        record Bucket(
                BigDecimal bac,
                BigDecimal pv,      // null means "at least one activity had null PV"
                BigDecimal ev,
                BigDecimal ac,
                int count
        ) {}

        Map<UUID, BigDecimal> bucketBac = new LinkedHashMap<>();
        Map<UUID, BigDecimal> bucketPv = new LinkedHashMap<>();   // null entry means "pv unknown"
        Map<UUID, BigDecimal> bucketEv = new LinkedHashMap<>();
        Map<UUID, BigDecimal> bucketAc = new LinkedHashMap<>();
        Map<UUID, Integer> bucketCount = new LinkedHashMap<>();
        Set<UUID> pvNull = new HashSet<>();  // buckets that have at least one null-PV activity

        for (Activity activity : activities) {
            // Resolve cost account: activity-level wins, then WBS node
            UUID caId = activity.getCostAccountId();
            if (caId == null && activity.getWbsNodeId() != null) {
                WbsNode wbs = wbsById.get(activity.getWbsNodeId());
                if (wbs != null) {
                    caId = wbs.getCostAccountId();
                }
            }
            // null caId → "Unassigned" bucket (represented by null key)

            EvmTechnique technique = resolveEvmTechnique(activity.getPercentCompleteType());
            EvmTechniqueStrategy strategy = EvmTechniqueFactory.getStrategy(technique);

            BigDecimal actBac = EvmRollupService.getActivityBac(activity, expensesByActivity, assignmentsByActivity);
            BigDecimal actPv = EvmRollupService.getActivityPv(activity, actBac, dataDate);
            BigDecimal actEv = strategy.calculateEarnedValue(activity, actBac, actPv);
            BigDecimal actAc = EvmRollupService.getActivityAc(activity, expensesByActivity, assignmentsByActivity);

            // Detect null PV: getActivityPv returns ZERO both for genuinely zero PV and for activities
            // whose dates make PV non-computable (no finish date, no start date, or dataDate before
            // plannedStartDate). All three cases must propagate null so the bucket's SV/SPI show null
            // rather than a falsely optimistic zero.
            boolean actPvNull = activity.getPlannedFinishDate() == null
                    || activity.getPlannedStartDate() == null
                    || dataDate.isBefore(activity.getPlannedStartDate());

            bucketBac.merge(caId, actBac, BigDecimal::add);
            bucketEv.merge(caId, actEv, BigDecimal::add);
            bucketAc.merge(caId, actAc, BigDecimal::add);
            bucketCount.merge(caId, 1, Integer::sum);

            if (actPvNull) {
                pvNull.add(caId);
                // Still accumulate zero so we can sum non-null contributors; the flag governs output
                bucketPv.merge(caId, BigDecimal.ZERO, BigDecimal::add);
            } else {
                bucketPv.merge(caId, actPv, BigDecimal::add);
            }
        }

        // Resolve cost account names/codes in one bulk call
        Set<UUID> assignedIds = bucketBac.keySet().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, CostAccount> caById = assignedIds.isEmpty()
                ? Collections.emptyMap()
                : costAccountRepository.findAllById(assignedIds).stream()
                        .collect(Collectors.toMap(c -> c.getId(), c -> c));

        // Build response rows
        List<CostAccountRollupResponse> rows = new ArrayList<>();

        for (UUID caId : bucketBac.keySet()) {
            BigDecimal bac = bucketBac.get(caId);
            BigDecimal pv = pvNull.contains(caId) ? null : bucketPv.get(caId);
            BigDecimal ev = bucketEv.get(caId);
            BigDecimal ac = bucketAc.get(caId);
            int count = bucketCount.get(caId);

            BigDecimal cv = ev.subtract(ac);
            BigDecimal sv = (pv != null) ? ev.subtract(pv) : null;
            BigDecimal cpi = (ac.compareTo(BigDecimal.ZERO) != 0)
                    ? ev.divide(ac, SCALE, RoundingMode.HALF_UP)
                    : null;
            BigDecimal spi = (pv != null && pv.compareTo(BigDecimal.ZERO) != 0)
                    ? ev.divide(pv, SCALE, RoundingMode.HALF_UP)
                    : null;

            String code;
            String name;
            if (caId == null) {
                code = null;
                name = "Unassigned";
            } else {
                CostAccount ca = caById.get(caId);
                code = (ca != null) ? ca.getCode() : caId.toString();
                name = (ca != null) ? ca.getName() : caId.toString();
            }

            rows.add(new CostAccountRollupResponse(
                    caId, code, name, bac, pv, ev, ac, cv, sv, cpi, spi, count));
        }

        // Sort: assigned buckets by code ascending, "Unassigned" (null caId) last
        rows.sort((a, b) -> {
            boolean aUnassigned = a.costAccountId() == null;
            boolean bUnassigned = b.costAccountId() == null;
            if (aUnassigned && bUnassigned) return 0;
            if (aUnassigned) return 1;
            if (bUnassigned) return -1;
            String codeA = a.costAccountCode() != null ? a.costAccountCode() : "";
            String codeB = b.costAccountCode() != null ? b.costAccountCode() : "";
            return codeA.compareToIgnoreCase(codeB);
        });

        return rows;
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
