package com.bipros.cost.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stateless RA Bill draft calculator. Lives in {@code bipros-cost} so the deduction math
 * stays close to the rest of the cost domain, but takes pre-fetched BOQ + previous-bill
 * data as input — never touches a repository. The cross-module orchestration (reading
 * BOQ from {@code bipros-project}, reading prior bills from {@code bipros-cost}) lives
 * in {@code RaBillDraftService} in {@code bipros-api}.
 *
 * <p>Mirrors the Phase 1.3 / {@code ActivityCostCalculator} pattern.
 */
public final class RaBillDraftCalculator {

  private RaBillDraftCalculator() {}

  /** Snapshot of one BOQ row at draft time — caller projects from {@code project.BoqItem}. */
  public record BoqLineSnapshot(
      UUID boqItemId,
      String itemNo,
      String description,
      String unit,
      BigDecimal boqRate,
      BigDecimal qtyExecutedToDate
  ) {}

  /** Deduction percentages applied to gross. All inputs are 0..1 fractions (e.g. 0.05 for 5%). */
  public record DeductionConfig(
      BigDecimal mobAdvanceRecoveryPct,
      BigDecimal retentionPct,
      BigDecimal tdsPct,
      BigDecimal gstPct
  ) {
    public static DeductionConfig defaults() {
      return new DeductionConfig(
          BigDecimal.ZERO,
          new BigDecimal("0.05"),
          new BigDecimal("0.02"),
          new BigDecimal("0.18"));
    }
  }

  /** One generated draft line. Frontend previews these before save. */
  public record RaBillItemDraft(
      UUID boqItemId,
      String itemCode,
      String description,
      String unit,
      BigDecimal rate,
      Double previousQuantity,
      Double currentQuantity,
      Double cumulativeQuantity,
      BigDecimal amount
  ) {}

  /** Outcome of a draft computation — items + grand totals. */
  public record DraftResult(
      List<RaBillItemDraft> items,
      BigDecimal grossAmount,
      BigDecimal mobAdvanceRecovery,
      BigDecimal retention5Pct,
      BigDecimal tds2Pct,
      BigDecimal gst18Pct,
      BigDecimal totalDeductions,
      BigDecimal netAmount
  ) {}

  /**
   * Compute a draft from a BOQ snapshot, the cumulative quantities from the previous
   * certified bill, and the deduction config. Lines whose Δqty ≤ 0 are skipped (no claim
   * this period). The {@code rate} on each draft line is taken from BOQ at this moment so
   * a later VO that revises a rate does NOT retroactively change drafts already saved.
   *
   * @param boq BOQ snapshot, in display order
   * @param previousCumulativeByBoqItemId map of boqItemId → cumulativeQuantity from the
   *                                      previous certified bill (empty when no prior bill)
   * @param deductions deduction percentages
   */
  public static DraftResult compute(
      List<BoqLineSnapshot> boq,
      Map<UUID, Double> previousCumulativeByBoqItemId,
      DeductionConfig deductions) {

    List<RaBillItemDraft> lines = new ArrayList<>();
    BigDecimal grossAmount = BigDecimal.ZERO;

    for (BoqLineSnapshot s : boq) {
      double current = s.qtyExecutedToDate() == null ? 0d : s.qtyExecutedToDate().doubleValue();
      double previous = previousCumulativeByBoqItemId.getOrDefault(s.boqItemId(), 0d);
      double delta = current - previous;
      if (delta <= 0d) continue;

      BigDecimal rate = s.boqRate() == null ? BigDecimal.ZERO : s.boqRate();
      BigDecimal amount = rate.multiply(BigDecimal.valueOf(delta)).setScale(2, RoundingMode.HALF_UP);
      grossAmount = grossAmount.add(amount);

      lines.add(new RaBillItemDraft(
          s.boqItemId(),
          s.itemNo(),
          s.description(),
          s.unit(),
          rate,
          previous,
          current,
          current,
          amount));
    }

    BigDecimal mobAdvance = pct(grossAmount, deductions.mobAdvanceRecoveryPct());
    BigDecimal retention = pct(grossAmount, deductions.retentionPct());
    BigDecimal tds = pct(grossAmount, deductions.tdsPct());
    BigDecimal gst = pct(grossAmount, deductions.gstPct());
    BigDecimal totalDeductions = mobAdvance.add(retention).add(tds).add(gst);
    BigDecimal netAmount = grossAmount.subtract(totalDeductions).setScale(2, RoundingMode.HALF_UP);

    return new DraftResult(
        lines, grossAmount, mobAdvance, retention, tds, gst, totalDeductions, netAmount);
  }

  private static BigDecimal pct(BigDecimal base, BigDecimal pct) {
    if (base == null || pct == null) return BigDecimal.ZERO;
    return base.multiply(pct).setScale(2, RoundingMode.HALF_UP);
  }
}
