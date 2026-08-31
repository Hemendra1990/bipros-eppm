package com.bipros.reporting.materialconsumption;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-function alert evaluator for the Material Consumption Report. Codes:
 * <ul>
 *   <li>{@code NEGATIVE_BALANCE} — balanceQty &lt; 0.</li>
 *   <li>{@code MISSING_UNIT_RATE} — consumedQty &gt; 0 AND (unitRate is null or ≤ 0).</li>
 * </ul>
 * Planned-dependent codes were removed when the report dropped its (invalid) planned construct.
 */
public final class MaterialConsumptionAlertEvaluator {

  public static final String NEGATIVE_BALANCE = "NEGATIVE_BALANCE";
  public static final String MISSING_UNIT_RATE = "MISSING_UNIT_RATE";

  private MaterialConsumptionAlertEvaluator() {}

  public static List<String> evaluate(
      BigDecimal consumedQty,
      BigDecimal balanceQty,
      BigDecimal unitRate) {
    List<String> alerts = new ArrayList<>(2);
    if (balanceQty != null && balanceQty.signum() < 0) {
      alerts.add(NEGATIVE_BALANCE);
    }
    if (nz(consumedQty).signum() > 0 && (unitRate == null || unitRate.signum() <= 0)) {
      alerts.add(MISSING_UNIT_RATE);
    }
    return alerts;
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
