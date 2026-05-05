package com.bipros.common.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Published by VariationOrderService when a VO transitions to APPROVED. Listeners run
 * via @TransactionalEventListener — phase varies by listener:
 * <ul>
 *   <li>BOQ mutation listener uses BEFORE_COMMIT so a mutation failure rolls the VO
 *       approval back, keeping {@code BoqItem} and {@code VariationOrder} consistent.</li>
 *   <li>Audit / requires-rebaseline listener uses AFTER_COMMIT — observation only, must
 *       not block the commit.</li>
 * </ul>
 *
 * <p>Header impact fields ({@code impactOnBudget}, {@code impactOnScheduleDays}) are
 * <em>advisory</em> — the system does not auto-apply them to activities or budgets.
 *
 * <p>{@link #lineItems} carries the structured BOQ mutations. Empty/null on legacy VOs
 * that do not yet use line items (Phase 9 keeps these working untouched).
 */
public record VariationOrderApprovedEvent(
    UUID voId,
    UUID contractId,
    UUID projectId,
    String voNumber,
    BigDecimal voValue,
    BigDecimal impactOnBudget,
    Integer impactOnScheduleDays,
    List<VoLineItemPayload> lineItems
) {
  public VariationOrderApprovedEvent {
    if (lineItems == null) {
      lineItems = List.of();
    }
  }
}
