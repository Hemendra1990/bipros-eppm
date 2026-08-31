package com.bipros.cost.application.service;

import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.resource.domain.model.ActivitySubContractorAssignment;
import com.bipros.resource.domain.model.ResourceAssignment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that {@link ActivityCostCalculator#calculatePlannedCost} and
 * {@link ActivityCostCalculator#calculateBudgetedCost} include the sub-contractor
 * assignment's plannedCost alongside resource assignments and expenses — the term that
 * was previously omitted from the per-activity rollup (project total/EVM already included it).
 */
class ActivityCostCalculatorTest {

  @Test
  void calculatePlannedCost_includesSubContractorPlannedCost() {
    UUID activityId = UUID.randomUUID();

    ResourceAssignment ra = new ResourceAssignment();
    ra.setActivityId(activityId);
    ra.setPlannedCost(new BigDecimal("100"));

    ActivitySubContractorAssignment sa = new ActivitySubContractorAssignment();
    sa.setActivityId(activityId);
    sa.setPlannedCost(new BigDecimal("25"));

    BigDecimal result = ActivityCostCalculator.calculatePlannedCost(
        activityId,
        Map.of(),
        Map.of(activityId, List.of(ra)),
        Map.of(activityId, List.of(sa)));

    assertEquals(0, new BigDecimal("125").compareTo(result));
  }

  @Test
  void calculatePlannedCost_unchanged_whenScMapHasNoEntryForActivity() {
    UUID activityId = UUID.randomUUID();

    ResourceAssignment ra = new ResourceAssignment();
    ra.setActivityId(activityId);
    ra.setPlannedCost(new BigDecimal("100"));

    BigDecimal result = ActivityCostCalculator.calculatePlannedCost(
        activityId,
        Map.of(),
        Map.of(activityId, List.of(ra)),
        Map.of());

    assertEquals(0, new BigDecimal("100").compareTo(result));
  }

  @Test
  void calculateBudgetedCost_includesSubContractorPlannedCost() {
    UUID activityId = UUID.randomUUID();

    ResourceAssignment ra = new ResourceAssignment();
    ra.setActivityId(activityId);
    ra.setBudgetedCost(new BigDecimal("100"));

    ActivitySubContractorAssignment sa = new ActivitySubContractorAssignment();
    sa.setActivityId(activityId);
    sa.setPlannedCost(new BigDecimal("25"));

    BigDecimal result = ActivityCostCalculator.calculateBudgetedCost(
        activityId,
        Map.of(),
        Map.of(activityId, List.of(ra)),
        Map.of(activityId, List.of(sa)));

    assertEquals(0, new BigDecimal("125").compareTo(result));
  }

  @Test
  void calculateBudgetedCost_unchanged_whenScMapHasNoEntryForActivity() {
    UUID activityId = UUID.randomUUID();

    ResourceAssignment ra = new ResourceAssignment();
    ra.setActivityId(activityId);
    ra.setBudgetedCost(new BigDecimal("100"));

    BigDecimal result = ActivityCostCalculator.calculateBudgetedCost(
        activityId,
        Map.of(),
        Map.of(activityId, List.of(ra)),
        Map.of());

    assertEquals(0, new BigDecimal("100").compareTo(result));
  }

  @Test
  void calculatePlannedCost_ignoresNullSubContractorPlannedCost() {
    UUID activityId = UUID.randomUUID();

    ActivitySubContractorAssignment sa = new ActivitySubContractorAssignment();
    sa.setActivityId(activityId);
    sa.setPlannedCost(null);

    BigDecimal result = ActivityCostCalculator.calculatePlannedCost(
        activityId,
        Map.of(),
        Map.of(),
        Map.of(activityId, List.of(sa)));

    assertEquals(0, BigDecimal.ZERO.compareTo(result));
  }

  @Test
  void summarize_threadsSubContractorMapIntoPlannedAndBudgetedCost() {
    UUID activityId = UUID.randomUUID();

    ActivityExpense e = new ActivityExpense();
    e.setActivityId(activityId);
    e.setBudgetedCost(new BigDecimal("10"));

    ResourceAssignment ra = new ResourceAssignment();
    ra.setActivityId(activityId);
    ra.setPlannedCost(new BigDecimal("100"));

    ActivitySubContractorAssignment sa = new ActivitySubContractorAssignment();
    sa.setActivityId(activityId);
    sa.setPlannedCost(new BigDecimal("25"));

    ActivityCostCalculator.ActivityCostSummary summary = ActivityCostCalculator.summarize(
        activityId,
        Map.of(activityId, List.of(e)),
        Map.of(activityId, List.of(ra)),
        Map.of(activityId, List.of(sa)));

    // plannedCost = expense budgetedCost(10) + resource plannedCost(100) + SC plannedCost(25)
    assertEquals(0, new BigDecimal("135").compareTo(summary.plannedCost()));
  }
}
