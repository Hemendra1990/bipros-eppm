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
 *   <li>actualAmount      = qtyExecutedToDate × actualRate</li>
 *   <li>percentComplete   = qtyExecutedToDate / boqQty (null when boqQty = 0)</li>
 *   <li>costVariance      = actualAmount − (qtyExecutedToDate × budgetedRate)</li>
 *   <li>costVariancePercent = costVariance / (qtyExecutedToDate × budgetedRate)
 *       (null when that denominator is 0, keeping "no earned budget yet ⇒ no variance %" explicit)</li>
 * </ul>
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
    BigDecimal actualAmount = round(qtyExecuted.multiply(actualRate));
    BigDecimal earnedBudget = qtyExecuted.multiply(budgetedRate);

    BigDecimal percentComplete = boqQty.signum() == 0
        ? null
        : qtyExecuted.divide(boqQty, RATIO_SCALE, RoundingMode.HALF_UP);

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

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static BigDecimal round(BigDecimal v) {
    return v.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
  }
}
