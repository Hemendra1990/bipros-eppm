package com.bipros.activity.application.percent;

import com.bipros.activity.domain.model.ActivityStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Hierarchy design §5.4 — a parent activity's progress is rolled up from its children,
 * weighted by each child's resource-plan planned cost (the same weighting principle the
 * BOQ/project rollups use). When every child's planned cost is zero the weights collapse,
 * so we fall back to the simple average (H6). Status is derived for display only:
 * all COMPLETED → COMPLETED; anything started → IN_PROGRESS; else NOT_STARTED.
 *
 * <p>Pure function of its inputs — no repository access — so the maths is unit-testable
 * in isolation ({@code ParentRollupCalculatorTest}).
 */
@Component
public class ParentRollupCalculator {

  public record ChildSnapshot(double percentComplete, BigDecimal plannedCost, ActivityStatus status) {}

  public record Result(double percentComplete, ActivityStatus derivedStatus) {}

  public Result rollup(List<ChildSnapshot> children) {
    if (children.isEmpty()) {
      return new Result(0.0, ActivityStatus.NOT_STARTED);
    }
    BigDecimal totalCost = children.stream()
        .map(c -> c.plannedCost() == null ? BigDecimal.ZERO : c.plannedCost())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    double pct;
    if (totalCost.signum() > 0) {
      BigDecimal weighted = children.stream()
          .map(c -> (c.plannedCost() == null ? BigDecimal.ZERO : c.plannedCost())
              .multiply(BigDecimal.valueOf(c.percentComplete())))
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      // 2 decimals — the stored value renders directly on the drawer/detail tiles, and the
      // BOQ listener's own change-detection granularity is 0.01 anyway.
      pct = weighted.divide(totalCost, 2, RoundingMode.HALF_UP).doubleValue();
    } else {
      double avg = children.stream().mapToDouble(ChildSnapshot::percentComplete).average().orElse(0.0);
      pct = Math.round(avg * 100.0) / 100.0;
    }
    boolean allCompleted = children.stream().allMatch(c -> c.status() == ActivityStatus.COMPLETED);
    boolean anyStarted = children.stream().anyMatch(c ->
        (c.status() != null && c.status() != ActivityStatus.NOT_STARTED) || c.percentComplete() > 0);
    ActivityStatus status = allCompleted ? ActivityStatus.COMPLETED
        : anyStarted ? ActivityStatus.IN_PROGRESS : ActivityStatus.NOT_STARTED;
    return new Result(pct, status);
  }
}
