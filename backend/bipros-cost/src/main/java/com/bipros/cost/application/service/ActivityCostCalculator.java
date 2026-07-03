package com.bipros.cost.application.service;

import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.resource.domain.model.ActivitySubContractorAssignment;
import com.bipros.resource.domain.model.ResourceAssignment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stateless rollup utility that aggregates per-activity cost metrics from {@link ActivityExpense}
 * and {@link ResourceAssignment} rows. Both sources contribute to all four metrics:
 *
 * <ul>
 *   <li>{@code plannedCost} — expenses' {@code budgetedCost} + assignments' {@code plannedCost}</li>
 *   <li>{@code actualCost} — expenses' {@code actualCost} + assignments' {@code actualCost}</li>
 *   <li>{@code remainingCost} — expenses' {@code remainingCost} + assignments' {@code remainingCost}</li>
 *   <li>{@code atCompletionCost} — expenses' {@code atCompletionCost} + assignments' {@code atCompletionCost};
 *       falls back to (actual + remaining) if the at-completion field is null</li>
 * </ul>
 *
 * <p>Note the asymmetry the planner needs to know about: {@link ActivityExpense} carries an
 * explicit {@code budgetedCost} but {@link ResourceAssignment} only has {@code plannedCost} —
 * Phase 2 of the baseline-progress roadmap closes that gap. Until then, this calculator treats
 * "planned" on an assignment as the budget contribution.
 *
 * <p>Designed to be reused by both {@code BaselineService} (during snapshot creation) and the new
 * {@code GET /v1/projects/{id}/activities/cost-summary} endpoint in {@code bipros-api}, so the
 * formula lives in exactly one place.
 */
public final class ActivityCostCalculator {

  private ActivityCostCalculator() {
    // static utility
  }

  /**
   * Per-activity rollup of the headline cost metrics.
   *
   * <p>Phase 2 added {@code budgetedCost} — the original committed value, frozen at assignment
   * creation. Re-planning {@code plannedCost} does not move {@code budgetedCost}; only an
   * explicit Re-budget action does. The grid uses both to compute "Budget Variance"
   * ({@code actualCost - budgetedCost}, positive = over-budget).
   */
  public record ActivityCostSummary(
      BigDecimal budgetedCost,
      BigDecimal plannedCost,
      BigDecimal actualCost,
      BigDecimal remainingCost,
      BigDecimal atCompletionCost
  ) {
    public static ActivityCostSummary zero() {
      return new ActivityCostSummary(
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
  }

  /**
   * Sum the budgeted cost contributions for one activity (Phase 2). Pulls
   * {@link ResourceAssignment#getBudgetedCost} for resource-driven costs and falls back to
   * {@link ActivityExpense#getBudgetedCost} for direct expenses (which already had the field).
   * Returns the original commitment, not the current plan.
   */
  public static BigDecimal calculateBudgetedCost(
      UUID activityId,
      Map<UUID, List<ActivityExpense>> expensesByActivity,
      Map<UUID, List<ResourceAssignment>> assignmentsByActivity,
      Map<UUID, List<ActivitySubContractorAssignment>> scByActivity) {
    BigDecimal cost = BigDecimal.ZERO;
    List<ActivityExpense> expenses = expensesByActivity.get(activityId);
    if (expenses != null) {
      for (ActivityExpense e : expenses) {
        if (e.getBudgetedCost() != null) cost = cost.add(e.getBudgetedCost());
      }
    }
    List<ResourceAssignment> assignments = assignmentsByActivity.get(activityId);
    if (assignments != null) {
      for (ResourceAssignment ra : assignments) {
        // Fall back to plannedCost when budgetedCost is null — handles legacy rows that
        // pre-date the Phase 2 backfill so callers always see a number, never an empty cell.
        BigDecimal contribution = ra.getBudgetedCost() != null
            ? ra.getBudgetedCost()
            : ra.getPlannedCost();
        if (contribution != null) cost = cost.add(contribution);
      }
    }
    List<ActivitySubContractorAssignment> scAssignments = scByActivity.get(activityId);
    if (scAssignments != null) {
      for (ActivitySubContractorAssignment sa : scAssignments) {
        if (sa.getPlannedCost() != null) cost = cost.add(sa.getPlannedCost());
      }
    }
    return cost;
  }

  /**
   * Sum the planned cost contributions for one activity. Mirrors the historical
   * {@code BaselineService.calculatePlannedCost} so the snapshot it produces stays comparable
   * with the live rollup.
   */
  public static BigDecimal calculatePlannedCost(
      UUID activityId,
      Map<UUID, List<ActivityExpense>> expensesByActivity,
      Map<UUID, List<ResourceAssignment>> assignmentsByActivity,
      Map<UUID, List<ActivitySubContractorAssignment>> scByActivity) {
    BigDecimal cost = BigDecimal.ZERO;
    List<ActivityExpense> expenses = expensesByActivity.get(activityId);
    if (expenses != null) {
      for (ActivityExpense e : expenses) {
        if (e.getBudgetedCost() != null) cost = cost.add(e.getBudgetedCost());
      }
    }
    List<ResourceAssignment> assignments = assignmentsByActivity.get(activityId);
    if (assignments != null) {
      for (ResourceAssignment ra : assignments) {
        if (ra.getPlannedCost() != null) cost = cost.add(ra.getPlannedCost());
      }
    }
    List<ActivitySubContractorAssignment> scAssignments = scByActivity.get(activityId);
    if (scAssignments != null) {
      for (ActivitySubContractorAssignment sa : scAssignments) {
        if (sa.getPlannedCost() != null) cost = cost.add(sa.getPlannedCost());
      }
    }
    return cost;
  }

  /**
   * Sum the actual cost contributions for one activity. Driven by Daily Outputs (which
   * populate {@code ResourceAssignment.actualCost}) and posted expenses.
   */
  public static BigDecimal calculateActualCost(
      UUID activityId,
      Map<UUID, List<ActivityExpense>> expensesByActivity,
      Map<UUID, List<ResourceAssignment>> assignmentsByActivity) {
    BigDecimal cost = BigDecimal.ZERO;
    List<ActivityExpense> expenses = expensesByActivity.get(activityId);
    if (expenses != null) {
      for (ActivityExpense e : expenses) {
        if (e.getActualCost() != null) cost = cost.add(e.getActualCost());
      }
    }
    List<ResourceAssignment> assignments = assignmentsByActivity.get(activityId);
    if (assignments != null) {
      for (ResourceAssignment ra : assignments) {
        if (ra.getActualCost() != null) cost = cost.add(ra.getActualCost());
      }
    }
    return cost;
  }

  /**
   * The ActivityExpense-only half of the canonical actual cost. Combine with the DPR ledger
   * (DprActualCostLookup.sumByActivity) to get the canonical per-activity AC used project-wide;
   * this deliberately excludes ResourceAssignment.actualCost, which mirrors the DPR ledger.
   */
  public static BigDecimal calculateExpenseActualCost(
      UUID activityId,
      Map<UUID, List<ActivityExpense>> expensesByActivity) {
    BigDecimal cost = BigDecimal.ZERO;
    List<ActivityExpense> expenses = expensesByActivity.get(activityId);
    if (expenses != null) {
      for (ActivityExpense e : expenses) {
        if (e.getActualCost() != null) cost = cost.add(e.getActualCost());
      }
    }
    return cost;
  }

  /** Sum the remaining cost contributions for one activity. */
  public static BigDecimal calculateRemainingCost(
      UUID activityId,
      Map<UUID, List<ActivityExpense>> expensesByActivity,
      Map<UUID, List<ResourceAssignment>> assignmentsByActivity) {
    BigDecimal cost = BigDecimal.ZERO;
    List<ActivityExpense> expenses = expensesByActivity.get(activityId);
    if (expenses != null) {
      for (ActivityExpense e : expenses) {
        if (e.getRemainingCost() != null) cost = cost.add(e.getRemainingCost());
      }
    }
    List<ResourceAssignment> assignments = assignmentsByActivity.get(activityId);
    if (assignments != null) {
      for (ResourceAssignment ra : assignments) {
        if (ra.getRemainingCost() != null) cost = cost.add(ra.getRemainingCost());
      }
    }
    return cost;
  }

  /**
   * Sum the at-completion cost contributions for one activity. When the underlying record's
   * {@code atCompletionCost} field is null, fall back to {@code actual + remaining} to keep the
   * EAC meaningful even on records that pre-date the at-completion column.
   */
  public static BigDecimal calculateAtCompletionCost(
      UUID activityId,
      Map<UUID, List<ActivityExpense>> expensesByActivity,
      Map<UUID, List<ResourceAssignment>> assignmentsByActivity) {
    BigDecimal cost = BigDecimal.ZERO;
    List<ActivityExpense> expenses = expensesByActivity.get(activityId);
    if (expenses != null) {
      for (ActivityExpense e : expenses) {
        cost = cost.add(eacFallback(
            e.getAtCompletionCost(), e.getActualCost(), e.getRemainingCost()));
      }
    }
    List<ResourceAssignment> assignments = assignmentsByActivity.get(activityId);
    if (assignments != null) {
      for (ResourceAssignment ra : assignments) {
        cost = cost.add(eacFallback(
            ra.getAtCompletionCost(), ra.getActualCost(), ra.getRemainingCost()));
      }
    }
    return cost;
  }

  /**
   * Compute all four metrics in a single pass — preferred for the cost-summary endpoint where
   * we want every activity's row even when only some metrics are populated.
   */
  public static ActivityCostSummary summarize(
      UUID activityId,
      Map<UUID, List<ActivityExpense>> expensesByActivity,
      Map<UUID, List<ResourceAssignment>> assignmentsByActivity,
      Map<UUID, List<ActivitySubContractorAssignment>> scByActivity) {
    return new ActivityCostSummary(
        calculateBudgetedCost(activityId, expensesByActivity, assignmentsByActivity, scByActivity),
        calculatePlannedCost(activityId, expensesByActivity, assignmentsByActivity, scByActivity),
        calculateActualCost(activityId, expensesByActivity, assignmentsByActivity),
        calculateRemainingCost(activityId, expensesByActivity, assignmentsByActivity),
        calculateAtCompletionCost(activityId, expensesByActivity, assignmentsByActivity));
  }

  private static BigDecimal eacFallback(
      BigDecimal atCompletion, BigDecimal actual, BigDecimal remaining) {
    if (atCompletion != null) return atCompletion;
    BigDecimal a = actual != null ? actual : BigDecimal.ZERO;
    BigDecimal r = remaining != null ? remaining : BigDecimal.ZERO;
    return a.add(r);
  }
}
