package com.bipros.resource.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One flattened row of the Unit Rate Master (Daily Cost Report – Section A): a single unit-of-cost
 * with its budgeted rate, actual rate, and derived variance. Equipment / Material / Sub-Contract
 * rows come from {@code Resource} + {@code ResourceRate}; Manpower rows come from
 * {@code ResourceRole}'s budgeted/actual fields.
 */
public record UnitRateMasterRow(
    UUID id,
    Source source,
    String category,
    String description,
    String unit,
    BigDecimal budgetedRate,
    BigDecimal actualRate,
    BigDecimal variance,
    BigDecimal variancePercent,
    String remarks
) {
  public enum Source { RESOURCE, RESOURCE_ROLE }
}
