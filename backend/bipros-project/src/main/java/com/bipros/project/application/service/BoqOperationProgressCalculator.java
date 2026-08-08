package com.bipros.project.application.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * The split math (split design 2026-08-03 §4.5, D3/D4). Pure — callers build {@link OpSnapshot}s
 * from {@code boq_operations} plus per-operation approved DPR sums and get back the line's two
 * derived numbers:
 * <ul>
 *   <li><b>earnedFraction</b> (0..1) — drives {@code percentComplete} and EV. WEIGHTED mode:
 *   Σ(wᵢ × min(eᵢ/tᵢ, 1)) ÷ Σwᵢ, per-operation clamp so one over-executed operation can't inflate
 *   the line; milestone operations (null target) are binary. PARTITION mode: Σeᵢ ÷ boqQty capped
 *   at 1 (weights ignored — the children partition the line's own quantity).</li>
 *   <li><b>measuredQty</b> — the billing/income/OVERRUN basis, RAW (never clamped). WEIGHTED mode:
 *   the measurement operation's executed qty (D3) plus the legacy operation's opening quantity
 *   (§7.3 — pre-split history was billable in the flat world and must not vanish at split time).
 *   PARTITION mode: Σ of all operations.</li>
 * </ul>
 */
@Component
public class BoqOperationProgressCalculator {

  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final BigDecimal ONE = BigDecimal.ONE;
  private static final RoundingMode HALF_UP = RoundingMode.HALF_UP;

  /** @param targetQty null = milestone operation (binary complete). */
  public record OpSnapshot(UUID id, BigDecimal targetQty, BigDecimal weightPct,
                           boolean isMeasure, boolean isLegacy, BigDecimal executedQty) { }

  public record Result(BigDecimal earnedFraction, BigDecimal measuredQty) { }

  public Result compute(String splitMode, BigDecimal boqQty, List<OpSnapshot> ops) {
    if ("QUANTITY_PARTITION".equals(splitMode)) {
      BigDecimal total = ops.stream().map(o -> nz(o.executedQty())).reduce(ZERO, BigDecimal::add);
      BigDecimal fraction = (boqQty == null || boqQty.signum() <= 0) ? ZERO
          : total.divide(boqQty, 6, HALF_UP).min(ONE);
      return new Result(fraction, total);            // partition: measured = Σ children (raw)
    }
    BigDecimal weightSum = ops.stream().map(o -> nz(o.weightPct())).reduce(ZERO, BigDecimal::add);
    BigDecimal weighted = ZERO;
    BigDecimal measured = ZERO;
    for (OpSnapshot o : ops) {
      BigDecimal p;
      if (o.targetQty() == null) {                    // edge 10: milestone op — binary
        p = nz(o.executedQty()).signum() > 0 ? ONE : ZERO;
      } else if (o.targetQty().signum() <= 0) {
        p = ZERO;
      } else {
        p = nz(o.executedQty()).divide(o.targetQty(), 6, HALF_UP).min(ONE);   // per-op clamp
      }
      weighted = weighted.add(nz(o.weightPct()).multiply(p));
      // RAW — OVERRUN survives (edge 9). Legacy = the pre-split opening quantity (§7.3):
      // history was billable before the split, so it stays in the measured basis.
      if (o.isMeasure() || o.isLegacy()) measured = measured.add(nz(o.executedQty()));
    }
    BigDecimal fraction = weightSum.signum() <= 0 ? ZERO
        : weighted.divide(weightSum, 6, HALF_UP).min(ONE); // normalise by Σw (edges 1+2); the
    // clamp is unreachable with valid weights (all ≥ 0, per-op p ≤ 1) — defense in depth only.
    return new Result(fraction, measured);
  }

  private static BigDecimal nz(BigDecimal v) {
    return v != null ? v : ZERO;
  }
}
