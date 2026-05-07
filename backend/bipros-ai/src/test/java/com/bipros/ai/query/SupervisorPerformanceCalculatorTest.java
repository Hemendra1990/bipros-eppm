package com.bipros.ai.query;

import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import com.bipros.evm.domain.repository.EvmCalculationRepository;
import com.bipros.project.domain.repository.DailyActivityResourceOutputRepository;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.udf.application.dto.FormulaResultDto;
import com.bipros.udf.application.service.FormulaEngine;
import com.bipros.udf.domain.model.FormulaOverride;
import com.bipros.udf.domain.repository.FormulaOverrideRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the cost-rollup path of {@link SupervisorPerformanceCalculator}.
 *
 * <p>The calculator's public {@code compute()} method orchestrates many subqueries;
 * these tests target {@link SupervisorPerformanceCalculator#computeCostRollup} directly
 * (package-private) to keep the mock surface small and focused on the formula/fallback
 * behaviour. Same-package access is JUnit-correct and intentional.
 */
@ExtendWith(MockitoExtension.class)
class SupervisorPerformanceCalculatorTest {

  @Mock private ActivityRepository activityRepository;
  @Mock private ResourceAssignmentRepository assignmentRepository;
  @Mock private EvmCalculationRepository evmRepository;
  @Mock private ActivityExpenseRepository expenseRepository;
  @Mock private DailyProgressReportRepository dprRepository;
  @Mock private DailyActivityResourceOutputRepository outputRepository;
  @Mock private ResourceRepository resourceRepository;
  @Mock private ResourceContextFacade facade;
  @Mock private FormulaEngine formulaEngine;
  @Mock private FormulaOverrideRepository formulaOverrideRepository;

  private SupervisorPerformanceCalculator calculator;

  private final UUID projectId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    calculator = new SupervisorPerformanceCalculator(
        activityRepository,
        assignmentRepository,
        evmRepository,
        expenseRepository,
        dprRepository,
        outputRepository,
        resourceRepository,
        facade,
        formulaEngine,
        formulaOverrideRepository);
  }

  @Test
  void emptyActivityListYieldsZeroRollupAndEmptyOverrides() {
    SupervisorPerformance.CostRollup r = calculator.computeCostRollup(projectId, List.of());

    assertThat(r.planned()).isEqualByComparingTo("0");
    assertThat(r.actual()).isEqualByComparingTo("0");
    assertThat(r.remaining()).isEqualByComparingTo("0");
    assertThat(r.atCompletion()).isEqualByComparingTo("0");
    assertThat(r.variance()).isEqualByComparingTo("0");
    assertThat(r.variancePct()).isNull();
    assertThat(r.overriddenFormulaCodes()).isEmpty();
  }

  @Test
  void defaultFormulaPathReproducesLegacyMath() {
    // Two assignments with simple, distinct rate × units.
    ResourceAssignment a1 = assignment(40.0, 10.0, 30.0, "320.00", "80.00", "240.00", "320.00");
    ResourceAssignment a2 = assignment(50.0, 50.0, 0.0,  "225.00", "225.00", "0.00", "225.00");
    when(assignmentRepository.findByActivityIdIn(anyList())).thenReturn(List.of(a1, a2));

    // Formula engine returns the values mathematically equivalent to the seeded master expressions.
    stubFormula("RES_PLANNED_COST", ctx -> ctx.get("RATE").multiply(ctx.get("PLANNED_UNITS")));
    stubFormula("RES_ACTUAL_COST",  ctx -> ctx.get("RATE").multiply(ctx.get("ACTUAL_UNITS")));
    stubFormula("RES_REMAINING_COST", ctx -> ctx.get("RATE").multiply(ctx.get("REMAINING_UNITS")));
    stubFormula("RES_AT_COMPLETION_COST", ctx -> ctx.get("ACTUAL_COST").add(ctx.get("REMAINING_COST")));
    stubFormula("SUP_COST_VARIANCE", ctx -> ctx.get("ACTUAL").subtract(ctx.get("PLANNED")));
    stubFormula("SUP_COST_VARIANCE_PCT", ctx -> {
      BigDecimal p = ctx.get("PLANNED");
      if (p.signum() == 0) return BigDecimal.ZERO;
      return ctx.get("ACTUAL").subtract(p).multiply(BigDecimal.valueOf(100))
          .divide(p, 4, java.math.RoundingMode.HALF_UP);
    });
    when(formulaOverrideRepository.findByFormulaCodeAndProjectId(anyString(), eq(projectId)))
        .thenReturn(Optional.empty());

    SupervisorPerformance.CostRollup r = calculator.computeCostRollup(
        projectId, List.of(UUID.randomUUID(), UUID.randomUUID()));

    // Planned = 320 + 225 = 545
    assertThat(r.planned()).isEqualByComparingTo("545.00");
    // Actual = (40h × 8 = 320 × 0.25)= 80; (50 × 4.5)=225 → 305
    assertThat(r.actual()).isEqualByComparingTo("305.00");
    // Variance = 305 - 545 = -240
    assertThat(r.variance()).isEqualByComparingTo("-240.00");
    assertThat(r.variancePct()).isCloseTo(-44.0367, offset(0.0001));
    assertThat(r.overriddenFormulaCodes()).isEmpty();
  }

  @Test
  void formulaErrorFallsBackToStoredCostFields() {
    ResourceAssignment a1 = assignment(40.0, 10.0, 30.0, "320.00", "80.00", "240.00", "320.00");
    when(assignmentRepository.findByActivityIdIn(anyList())).thenReturn(List.of(a1));

    // Every formula evaluation returns an error → calculator must fall back.
    when(formulaEngine.evaluate(anyString(), eq(projectId), any()))
        .thenReturn(FormulaResultDto.builder().error(true).errorMessage("simulated").build());
    when(formulaOverrideRepository.findByFormulaCodeAndProjectId(anyString(), eq(projectId)))
        .thenReturn(Optional.empty());

    SupervisorPerformance.CostRollup r = calculator.computeCostRollup(
        projectId, List.of(UUID.randomUUID()));

    // Falls back to stored fields exactly.
    assertThat(r.planned()).isEqualByComparingTo("320.00");
    assertThat(r.actual()).isEqualByComparingTo("80.00");
    assertThat(r.remaining()).isEqualByComparingTo("240.00");
    assertThat(r.atCompletion()).isEqualByComparingTo("320.00");
    // Variance fallback: 80 - 320 = -240
    assertThat(r.variance()).isEqualByComparingTo("-240.00");
    assertThat(r.variancePct()).isCloseTo(-75.0, offset(0.0001));
  }

  @Test
  void activeOverrideIsReportedInOverriddenFormulaCodes() {
    when(assignmentRepository.findByActivityIdIn(anyList())).thenReturn(List.of());
    // Empty assignments → still need to query overrides. Wire one active override.
    FormulaOverride active = new FormulaOverride();
    active.setFormulaCode("RES_ACTUAL_COST");
    active.setProjectId(projectId);
    active.setOverrideExpression("$RATE * $ACTUAL_UNITS * 1.1");
    active.setIsActive(true);
    active.setEffectiveFrom(LocalDate.now().minusDays(1));
    when(formulaOverrideRepository.findByFormulaCodeAndProjectId("RES_ACTUAL_COST", projectId))
        .thenReturn(Optional.of(active));
    when(formulaOverrideRepository.findByFormulaCodeAndProjectId(
        org.mockito.ArgumentMatchers.argThat(s -> s != null && !s.equals("RES_ACTUAL_COST")),
        eq(projectId))).thenReturn(Optional.empty());

    SupervisorPerformance.CostRollup r = calculator.computeCostRollup(
        projectId, List.of(UUID.randomUUID()));

    assertThat(r.overriddenFormulaCodes()).containsExactly("RES_ACTUAL_COST");
  }

  @Test
  void inactiveOrOutOfWindowOverrideIsIgnored() {
    when(assignmentRepository.findByActivityIdIn(anyList())).thenReturn(List.of());

    FormulaOverride inactive = new FormulaOverride();
    inactive.setFormulaCode("RES_PLANNED_COST");
    inactive.setProjectId(projectId);
    inactive.setIsActive(false);
    when(formulaOverrideRepository.findByFormulaCodeAndProjectId("RES_PLANNED_COST", projectId))
        .thenReturn(Optional.of(inactive));

    FormulaOverride expired = new FormulaOverride();
    expired.setFormulaCode("SUP_COST_VARIANCE");
    expired.setProjectId(projectId);
    expired.setIsActive(true);
    expired.setEffectiveFrom(LocalDate.now().minusDays(30));
    expired.setEffectiveTo(LocalDate.now().minusDays(1));
    when(formulaOverrideRepository.findByFormulaCodeAndProjectId("SUP_COST_VARIANCE", projectId))
        .thenReturn(Optional.of(expired));

    when(formulaOverrideRepository.findByFormulaCodeAndProjectId(
        org.mockito.ArgumentMatchers.argThat(s ->
            s != null && !s.equals("RES_PLANNED_COST") && !s.equals("SUP_COST_VARIANCE")),
        eq(projectId))).thenReturn(Optional.empty());

    SupervisorPerformance.CostRollup r = calculator.computeCostRollup(
        projectId, List.of(UUID.randomUUID()));

    assertThat(r.overriddenFormulaCodes()).isEmpty();
  }

  @Test
  void nullActualOrRemainingUnitsDoNotNpe() {
    ResourceAssignment a = new ResourceAssignment();
    a.setPlannedUnits(40.0);
    a.setActualUnits(null);     // legacy row
    a.setRemainingUnits(null);
    a.setPlannedCost(new BigDecimal("320.00"));
    a.setActualCost(null);
    a.setRemainingCost(null);
    a.setAtCompletionCost(null);
    when(assignmentRepository.findByActivityIdIn(anyList())).thenReturn(List.of(a));

    // Stub a passthrough engine so we exercise the formula path with null-safe context.
    stubFormula("RES_PLANNED_COST", ctx -> ctx.get("RATE").multiply(ctx.get("PLANNED_UNITS")));
    stubFormula("RES_ACTUAL_COST",  ctx -> ctx.get("RATE").multiply(ctx.get("ACTUAL_UNITS")));
    stubFormula("RES_REMAINING_COST", ctx -> ctx.get("RATE").multiply(ctx.get("REMAINING_UNITS")));
    stubFormula("RES_AT_COMPLETION_COST", ctx -> ctx.get("ACTUAL_COST").add(ctx.get("REMAINING_COST")));
    stubFormula("SUP_COST_VARIANCE", ctx -> ctx.get("ACTUAL").subtract(ctx.get("PLANNED")));
    stubFormula("SUP_COST_VARIANCE_PCT", ctx -> BigDecimal.ZERO);
    when(formulaOverrideRepository.findByFormulaCodeAndProjectId(anyString(), eq(projectId)))
        .thenReturn(Optional.empty());

    SupervisorPerformance.CostRollup r = calculator.computeCostRollup(
        projectId, List.of(UUID.randomUUID()));

    // Null actual/remaining units → those formula evaluations multiply by 0 → 0 contribution.
    assertThat(r.planned()).isEqualByComparingTo("320.00");
    assertThat(r.actual()).isEqualByComparingTo("0");
    assertThat(r.remaining()).isEqualByComparingTo("0");
    assertThat(r.atCompletion()).isEqualByComparingTo("0");
  }

  @Test
  void plannedZeroProducesNullVariancePct() {
    ResourceAssignment a = assignment(0.0, 0.0, 0.0, "0", "0", "0", "0");
    when(assignmentRepository.findByActivityIdIn(anyList())).thenReturn(List.of(a));

    stubFormula("RES_PLANNED_COST", ctx -> BigDecimal.ZERO);
    stubFormula("RES_ACTUAL_COST", ctx -> BigDecimal.ZERO);
    stubFormula("RES_REMAINING_COST", ctx -> BigDecimal.ZERO);
    stubFormula("RES_AT_COMPLETION_COST", ctx -> BigDecimal.ZERO);
    stubFormula("SUP_COST_VARIANCE", ctx -> BigDecimal.ZERO);
    // Engine returns 0 for variance pct (per the seeded IF($PLANNED=0,0,…) formula),
    // but the calculator should still surface null because planned is 0.
    stubFormula("SUP_COST_VARIANCE_PCT", ctx -> BigDecimal.ZERO);
    when(formulaOverrideRepository.findByFormulaCodeAndProjectId(anyString(), eq(projectId)))
        .thenReturn(Optional.empty());

    SupervisorPerformance.CostRollup r = calculator.computeCostRollup(
        projectId, List.of(UUID.randomUUID()));

    assertThat(r.planned()).isEqualByComparingTo("0");
    assertThat(r.variancePct()).isNull();
  }

  // ---- helpers ----

  private static ResourceAssignment assignment(
      double plannedUnits, double actualUnits, double remainingUnits,
      String plannedCost, String actualCost, String remainingCost, String atCompletionCost) {
    ResourceAssignment a = new ResourceAssignment();
    a.setPlannedUnits(plannedUnits);
    a.setActualUnits(actualUnits);
    a.setRemainingUnits(remainingUnits);
    a.setPlannedCost(new BigDecimal(plannedCost));
    a.setActualCost(new BigDecimal(actualCost));
    a.setRemainingCost(new BigDecimal(remainingCost));
    a.setAtCompletionCost(new BigDecimal(atCompletionCost));
    return a;
  }

  /**
   * Stubs {@link FormulaEngine#evaluate} for a given code with a passthrough function over
   * the context map, so the test reads exactly like the seeded master expression.
   */
  private void stubFormula(String code, java.util.function.Function<Map<String, BigDecimal>, BigDecimal> fn) {
    lenient().when(formulaEngine.evaluate(eq(code), eq(projectId), any())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked")
      Map<String, BigDecimal> ctx = (Map<String, BigDecimal>) inv.getArgument(2);
      BigDecimal v = fn.apply(ctx);
      return FormulaResultDto.builder().formulaCode(code).value(v).error(false).build();
    });
  }
}
