package com.bipros.project.application.service;

import com.bipros.project.domain.model.BoqItem;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure calculator for the six BOQ derived fields. Held out as a static utility so the formulas
 * can be unit-tested in isolation and so the same math is reused by both the REST service and the
 * seeder — preventing drift between the two write paths.
 *
 * <p>Rules (all input BigDecimals are treated as zero when null):
 * <ul>
 *   <li>boqAmount         = boqQty × boqRate</li>
 *   <li>budgetedAmount    = boqQty × budgetedRate</li>
 *   <li>actualAmount      = actualCost when the DPR roll-up has set one, else
 *       qtyExecutedToDate × actualRate (seeded / manually rated lines)</li>
 *   <li>percentComplete   = qtyExecutedToDate / boqQty (null when boqQty = 0)</li>
 *   <li>costVariance      = actualAmount − {@link #earnedBudget(BoqItem)}</li>
 *   <li>costVariancePercent = costVariance / earnedBudget
 *       (null when that denominator is 0, keeping "no earned budget yet ⇒ no variance %" explicit)</li>
 * </ul>
 *
 * <p><b>Why actualCost wins (11 Aug 2026).</b> {@code actualAmount} used to be reconstructed as
 * {@code qty × rate} on every write. On a split line {@code qtyExecutedToDate} counts only the
 * measurement operation, so a line whose spend sat entirely on a non-measurement stage stored
 * {@code 0 × rate = 0} and reported no cost at all while its earned budget was credited in full —
 * the same work counted as earned and as costless. Cost incurred is now carried in its own field
 * and is never a function of the billable quantity. {@code qtyExecutedToDate} keeps its
 * measurement/invoice meaning untouched.
 */
public final class BoqCalculator {

  private static final int AMOUNT_SCALE = 2;
  private static final int RATIO_SCALE = 6;

  private BoqCalculator() {}

  /** Recomputes and sets all derived fields on the item. Pass-through on null inputs. */
  public static void recompute(BoqItem item) {
    BigDecimal boqQty = nz(item.getBoqQty());
    BigDecimal boqRate = nz(item.getBoqRate());
    BigDecimal budgetedRate = nz(item.getBudgetedRate());
    BigDecimal qtyExecuted = nz(item.getQtyExecutedToDate());
    BigDecimal actualRate = nz(item.getActualRate());

    BigDecimal boqAmount = round(boqQty.multiply(boqRate));
    BigDecimal budgetedAmount = round(boqQty.multiply(budgetedRate));
    // Cost incurred wins whenever the DPR roll-up has established one — it is real money and must
    // not be gated on the billable quantity. Lines the roll-up never touched (seeded rows, manually
    // rated rows) keep the legacy rate × qty basis.
    BigDecimal actualAmount = item.getActualCost() != null
        ? round(item.getActualCost())
        : round(qtyExecuted.multiply(actualRate));
    BigDecimal earnedBudget = earnedBudget(item);

    BigDecimal percentComplete;
    if (item.getEarnedFraction() != null) {
      // Split line (§4.4): the weighted operation fraction IS the completion. qtyExecutedToDate
      // stays the raw measured quantity — billing basis, not the % basis.
      percentComplete = item.getEarnedFraction().setScale(RATIO_SCALE, RoundingMode.HALF_UP);
    } else {
      BigDecimal cappedQty = boqQty.signum() == 0 ? qtyExecuted : qtyExecuted.min(boqQty);
      percentComplete = boqQty.signum() == 0
          ? null
          : cappedQty.divide(boqQty, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    BigDecimal costVariance = round(actualAmount.subtract(earnedBudget));

    BigDecimal costVariancePercent = earnedBudget.signum() == 0
        ? null
        : costVariance.divide(earnedBudget, RATIO_SCALE, RoundingMode.HALF_UP);

    item.setBoqAmount(boqAmount);
    item.setBudgetedAmount(budgetedAmount);
    item.setActualAmount(actualAmount);
    item.setPercentComplete(percentComplete);
    item.setCostVariance(costVariance);
    item.setCostVariancePercent(costVariancePercent);
  }

  /**
   * The line's earned-budget basis for {@code costVariance} (Gate A, approved 04 Aug 2026):
   * split line ⇒ {@code earnedFraction × boqQty × budgetedRate}; else {@code min(qty, boqQty) ×
   * budgetedRate} — budget is never credited beyond the contracted quantity. A zero/null
   * {@code boqQty} keeps the legacy uncapped basis (degenerate lines: no contract qty to cap at).
   * Shared by {@link #recompute} and the BOQ-tab grand total so Σ(per-line CV) always equals the
   * grand variance.
   */
  public static BigDecimal earnedBudget(BoqItem item) {
    BigDecimal boqQty = nz(item.getBoqQty());
    BigDecimal budgetedRate = nz(item.getBudgetedRate());
    BigDecimal qtyExecuted = nz(item.getQtyExecutedToDate());
    if (item.getEarnedFraction() != null) {
      return item.getEarnedFraction().multiply(boqQty).multiply(budgetedRate);
    }
    BigDecimal cappedQty = boqQty.signum() == 0 ? qtyExecuted : qtyExecuted.min(boqQty);
    return cappedQty.multiply(budgetedRate);
  }

  /** Concept-A "progress/EVM earned" for one line: min(qty, boqQty) × budgetedRate, UNROUNDED
   *  to match the SQL SUM aggregates. A null boqQty is a zero-BAC line ⇒ earns 0 (matches the
   *  `boqQty IS NOT NULL` guards in sumEarnedBudgetedValue / sumQtyByBoqItemAndDate). Null qty/rate ⇒ 0. */
  public static BigDecimal cappedEarned(BigDecimal qtyExecuted, BigDecimal boqQty, BigDecimal budgetedRate) {
    if (boqQty == null) return BigDecimal.ZERO;
    return nz(qtyExecuted).min(boqQty).multiply(nz(budgetedRate));
  }

  /** Split-aware twin of {@code BoqItemRepository.sumEarnedBudgetedValue}'s per-line CASE:
   *  earnedFraction present ⇒ fraction × boqQty × budgetedRate, else the capped formula above.
   *  The SQL and this method must stay identical or the Costs tab and BOQ tab disagree. */
  public static BigDecimal cappedEarned(BigDecimal earnedFraction, BigDecimal qtyExecuted,
                                        BigDecimal boqQty, BigDecimal budgetedRate) {
    if (earnedFraction != null) {
      if (boqQty == null) return BigDecimal.ZERO;
      return earnedFraction.multiply(boqQty).multiply(nz(budgetedRate));
    }
    return cappedEarned(qtyExecuted, boqQty, budgetedRate);
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static BigDecimal round(BigDecimal v) {
    return v.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
  }
}
