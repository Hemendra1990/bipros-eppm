package com.bipros.activity.application.percent;

import com.bipros.activity.domain.model.ActivityStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hierarchy design §5.4: parent % = Σ(child plannedCost × child %) ÷ Σ(child plannedCost),
 * falling back to the simple average when every child's planned cost is zero (H6). Status is
 * derived: all COMPLETED → COMPLETED; any started → IN_PROGRESS; else NOT_STARTED.
 */
class ParentRollupCalculatorTest {

  private final ParentRollupCalculator calc = new ParentRollupCalculator();

  @Test
  void costWeightedAverage() {
    // (100×50 + 300×100) / 400 = 87.5
    var r = calc.rollup(List.of(
        snap(50.0, "100", ActivityStatus.IN_PROGRESS),
        snap(100.0, "300", ActivityStatus.COMPLETED)));
    assertEquals(87.5, r.percentComplete(), 1e-9);
    assertEquals(ActivityStatus.IN_PROGRESS, r.derivedStatus());
  }

  @Test
  void allZeroCostFallsBackToSimpleAverage() {
    var r = calc.rollup(List.of(
        snap(40.0, "0", ActivityStatus.IN_PROGRESS),
        snap(80.0, "0", ActivityStatus.IN_PROGRESS)));
    assertEquals(60.0, r.percentComplete(), 1e-9);
  }

  @Test
  void zeroCostChildIsExcludedFromTheWeightedAverage() {
    // Weighted mode ignores the 0-cost child: (200×30)/200 = 30
    var r = calc.rollup(List.of(
        snap(90.0, "0", ActivityStatus.IN_PROGRESS),
        snap(30.0, "200", ActivityStatus.IN_PROGRESS)));
    assertEquals(30.0, r.percentComplete(), 1e-9);
  }

  @Test
  void allCompletedDerivesCompleted() {
    var r = calc.rollup(List.of(
        snap(100.0, "10", ActivityStatus.COMPLETED),
        snap(100.0, "0", ActivityStatus.COMPLETED)));
    assertEquals(ActivityStatus.COMPLETED, r.derivedStatus());
    assertEquals(100.0, r.percentComplete(), 1e-9);
  }

  @Test
  void noneStartedDerivesNotStarted() {
    var r = calc.rollup(List.of(snap(0.0, "10", ActivityStatus.NOT_STARTED)));
    assertEquals(ActivityStatus.NOT_STARTED, r.derivedStatus());
    assertEquals(0.0, r.percentComplete(), 1e-9);
  }

  @Test
  void progressWithoutStatusStillCountsAsStarted() {
    var r = calc.rollup(List.of(snap(10.0, "10", ActivityStatus.NOT_STARTED)));
    assertEquals(ActivityStatus.IN_PROGRESS, r.derivedStatus());
  }

  @Test
  void emptyChildrenIsZeroNotStarted() {
    var r = calc.rollup(List.of());
    assertEquals(0.0, r.percentComplete(), 1e-9);
    assertEquals(ActivityStatus.NOT_STARTED, r.derivedStatus());
  }

  @Test
  void nullCostTreatedAsZero() {
    var r = calc.rollup(List.of(
        new ParentRollupCalculator.ChildSnapshot(50.0, null, ActivityStatus.IN_PROGRESS),
        snap(100.0, "100", ActivityStatus.COMPLETED)));
    assertEquals(100.0, r.percentComplete(), 1e-9);
  }

  private static ParentRollupCalculator.ChildSnapshot snap(double pct, String cost, ActivityStatus s) {
    return new ParentRollupCalculator.ChildSnapshot(pct, new BigDecimal(cost), s);
  }
}
