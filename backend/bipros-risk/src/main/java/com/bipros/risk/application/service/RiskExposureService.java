package com.bipros.risk.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskActivityAssignment;
import com.bipros.risk.domain.model.RiskProbability;
import com.bipros.risk.domain.model.RiskScoringConfig;
import com.bipros.risk.domain.repository.RiskActivityAssignmentRepository;
import com.bipros.risk.domain.repository.RiskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Recalculates a risk's exposure dates and costs based on assigned activities.
 * Aligned with Primavera P6 logic:
 * <ul>
 *   <li>Exposure Start = MIN(assigned activity start dates)</li>
 *   <li>Exposure Finish = MAX(assigned activity finish dates)</li>
 *   <li>Pre-Response Exposure Cost = Σ(activity budgeted cost) × P% × Impact%</li>
 *   <li>Post-Response Exposure Cost = Σ(activity budgeted cost) × post-P% × post-Impact%</li>
 * </ul>
 *
 * <p>The probability and impact percentages below are P6 illustrative defaults; making
 * them per-project configurable is tracked separately.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RiskExposureService {

    private final RiskActivityAssignmentRepository assignmentRepository;
    private final RiskRepository riskRepository;
    private final ActivityRepository activityRepository;
    private final ActivityExpenseRepository expenseRepository;
    private final RiskScoringMatrixService matrixService;

    /**
     * Recalculate exposure dates AND costs for a risk in a single save.
     */
    public void recalculateAll(UUID riskId) {
        Risk risk = riskRepository.findById(riskId).orElse(null);
        if (risk == null) return;

        List<RiskActivityAssignment> assignments = assignmentRepository.findByRiskId(riskId);
        if (assignments.isEmpty()) {
            risk.setExposureStartDate(null);
            risk.setExposureFinishDate(null);
            risk.setPreResponseExposureCost(null);
            risk.setPostResponseExposureCost(null);
            riskRepository.save(risk);
            return;
        }

        List<UUID> activityIds = assignments.stream()
                .map(RiskActivityAssignment::getActivityId)
                .toList();
        List<Activity> activities = activityRepository.findByIdIn(activityIds);
        applyDates(risk, activities);
        applyCosts(risk, activityIds);
        riskRepository.save(risk);

        log.debug("Risk {} recalculated: start={}, finish={}, preCost={}, postCost={}",
            riskId, risk.getExposureStartDate(), risk.getExposureFinishDate(),
            risk.getPreResponseExposureCost(), risk.getPostResponseExposureCost());
    }

    /**
     * Refresh every risk linked to a given activity. Callers (Activity update,
     * ActivityExpense update) invoke this to keep risk exposure fresh.
     */
    public void recalculateForActivity(UUID activityId) {
        Set<UUID> riskIds = assignmentRepository.findByActivityId(activityId).stream()
            .map(RiskActivityAssignment::getRiskId)
            .collect(Collectors.toSet());
        for (UUID riskId : riskIds) {
            recalculateAll(riskId);
        }
    }

    /**
     * @deprecated use {@link #recalculateAll(UUID)} which folds dates and costs into one save.
     */
    @Deprecated
    public void recalculateExposureDates(UUID riskId) { recalculateAll(riskId); }

    /**
     * @deprecated use {@link #recalculateAll(UUID)} which folds dates and costs into one save.
     */
    @Deprecated
    public void recalculateExposureCost(UUID riskId) { recalculateAll(riskId); }

    private void applyDates(Risk risk, List<Activity> activities) {
        if (activities.isEmpty()) {
            risk.setExposureStartDate(null);
            risk.setExposureFinishDate(null);
            return;
        }
        LocalDate earliestStart = activities.stream()
                .map(a -> a.getPlannedStartDate() != null ? a.getPlannedStartDate() : a.getEarlyStartDate())
                .filter(d -> d != null)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate latestFinish = activities.stream()
                .map(a -> a.getPlannedFinishDate() != null ? a.getPlannedFinishDate() : a.getEarlyFinishDate())
                .filter(d -> d != null)
                .max(LocalDate::compareTo)
                .orElse(null);
        risk.setExposureStartDate(earliestStart);
        risk.setExposureFinishDate(latestFinish);
    }

    private void applyCosts(Risk risk, List<UUID> activityIds) {
        List<ActivityExpense> expenses = expenseRepository.findByProjectIdAndActivityIdIn(
                risk.getProjectId(), activityIds);
        BigDecimal totalBudgetedCost = expenses.stream()
                .map(ActivityExpense::getBudgetedCost)
                .filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalBudgetedCost.compareTo(BigDecimal.ZERO) == 0) {
            risk.setPreResponseExposureCost(BigDecimal.ZERO);
            risk.setPostResponseExposureCost(BigDecimal.ZERO);
            return;
        }

        BigDecimal preExposureCost = calculateExposureCost(
                totalBudgetedCost,
                risk.getProbability(),
                risk.getImpactCost(),
                risk.getImpactSchedule(),
                risk.getProjectId());
        BigDecimal postExposureCost = calculateExposureCost(
                totalBudgetedCost,
                risk.getPostResponseProbability(),
                risk.getPostResponseImpactCost(),
                risk.getPostResponseImpactSchedule(),
                risk.getProjectId());
        risk.setPreResponseExposureCost(preExposureCost);
        risk.setPostResponseExposureCost(postExposureCost);
    }

    private BigDecimal calculateExposureCost(
            BigDecimal totalBudgetedCost,
            RiskProbability probability,
            Integer impactCost,
            Integer impactSchedule,
            UUID projectId) {

        if (probability == null) return BigDecimal.ZERO;

        double probabilityPct = switch (probability) {
            case VERY_HIGH -> 0.95;
            case HIGH -> 0.60;
            case MEDIUM -> 0.40;
            case LOW -> 0.20;
            case VERY_LOW -> 0.05;
        };

        RiskScoringConfig config = matrixService.getConfig(projectId);
        int derivedImpact = Risk.deriveImpact(impactCost, impactSchedule, config.getScoringMethod());

        double impactPct = switch (derivedImpact) {
            case 5 -> 0.40;
            case 4 -> 0.20;
            case 3 -> 0.10;
            case 2 -> 0.05;
            case 1 -> 0.02;
            default -> 0.05;
        };

        return totalBudgetedCost
                .multiply(BigDecimal.valueOf(probabilityPct))
                .multiply(BigDecimal.valueOf(impactPct))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
