package com.bipros.reporting.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Math contract for the supervisor performance rollup. Pins the SC180 semantics:
 * <ul>
 *   <li>Utilization % = budgetedDays / actualDays × 100 — UNCAPPED and scale-4 division,
 *       byte-identical to CapacityUtilizationReportService.buildPeriod (CAP-21 unification:
 *       the same trade must never show different Eff% on the two report surfaces).</li>
 *   <li>Cost implication = (actualDays - budgetedDays) × rate, 2dp.</li>
 *   <li>Missing inputs propagate as null — never zero, so the UI can render "—".</li>
 * </ul>
 */
@DisplayName("SupervisorPerformanceReportService — math")
class SupervisorPerformanceMathTest {

  @Nested
  @DisplayName("computeUtilizationPct")
  class Utilization {

    @Test
    @DisplayName("efficient crew (budget 8 / actual 6 days) → 133.33%")
    void efficientCrewOver100() {
      BigDecimal pct = SupervisorPerformanceReportService.computeUtilizationPct(
          new BigDecimal("8"), new BigDecimal("6"));
      assertThat(pct).isEqualByComparingTo(new BigDecimal("133.33"));
    }

    @Test
    @DisplayName("slow crew (budget 6 / actual 8 days) → 75.00%")
    void slowCrewUnder100() {
      BigDecimal pct = SupervisorPerformanceReportService.computeUtilizationPct(
          new BigDecimal("6"), new BigDecimal("8"));
      assertThat(pct).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    @DisplayName("exactly on plan → 100.00%")
    void exactlyOnPlan() {
      BigDecimal pct = SupervisorPerformanceReportService.computeUtilizationPct(
          new BigDecimal("10"), new BigDecimal("10"));
      assertThat(pct).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("crew far above plan is NOT capped — matches the Capacity tab (CAP-21)")
    void uncappedAbovePlan() {
      BigDecimal pct = SupervisorPerformanceReportService.computeUtilizationPct(
          new BigDecimal("1000"), new BigDecimal("1"));
      assertThat(pct).isEqualByComparingTo(new BigDecimal("100000"));
    }

    @Test
    @DisplayName("CAP-21 regression: the live Carpenter row (budget 130.905 / counted 53) → 246.99%")
    void carpenterRegression() {
      // The same inputs the Capacity tab showed as 247% must produce the same figure here —
      // before unification this surface said 79.1% for the identical crew and window.
      BigDecimal pct = SupervisorPerformanceReportService.computeUtilizationPct(
          new BigDecimal("130.905"), new BigDecimal("53"));
      assertThat(pct).isEqualByComparingTo(new BigDecimal("246.99"));
    }

    @Test
    @DisplayName("missing norm (budgeted null) → null, not 0")
    void missingNormReturnsNull() {
      assertThat(SupervisorPerformanceReportService.computeUtilizationPct(
          null, new BigDecimal("8"))).isNull();
    }

    @Test
    @DisplayName("zero actual days → null (avoid divide-by-zero)")
    void zeroActualReturnsNull() {
      assertThat(SupervisorPerformanceReportService.computeUtilizationPct(
          new BigDecimal("8"), BigDecimal.ZERO)).isNull();
    }
  }

  @Nested
  @DisplayName("computeCostImplication")
  class CostImplication {

    @Test
    @DisplayName("over-spent (actual > budget) → positive cost impact")
    void overSpent() {
      // Used 8 actual days, budgeted 6, rate ₹100/day → (8-6)*100 = ₹200 over.
      BigDecimal impact = SupervisorPerformanceReportService.computeCostImplication(
          new BigDecimal("8"), new BigDecimal("6"), new BigDecimal("100"));
      assertThat(impact).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("saved (actual < budget) → negative cost impact")
    void saved() {
      // Used 6 actual days, budgeted 8, rate ₹100/day → (6-8)*100 = -₹200 saved.
      BigDecimal impact = SupervisorPerformanceReportService.computeCostImplication(
          new BigDecimal("6"), new BigDecimal("8"), new BigDecimal("100"));
      assertThat(impact).isEqualByComparingTo(new BigDecimal("-200.00"));
    }

    @Test
    @DisplayName("missing rate → null")
    void missingRate() {
      assertThat(SupervisorPerformanceReportService.computeCostImplication(
          new BigDecimal("8"), new BigDecimal("6"), null)).isNull();
    }

    @Test
    @DisplayName("missing budget (no norm) → null (utilization unknown so cost impact unknowable)")
    void missingBudget() {
      assertThat(SupervisorPerformanceReportService.computeCostImplication(
          new BigDecimal("8"), null, new BigDecimal("100"))).isNull();
    }
  }
}
