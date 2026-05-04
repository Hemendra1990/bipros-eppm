package com.bipros.common.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published by VariationOrderService when a VO transitions to APPROVED. Listeners run
 * via @TransactionalEventListener(AFTER_COMMIT) so the new status is already committed.
 *
 * <p>The impact fields ({@code impactOnBudget}, {@code impactOnScheduleDays}) are
 * <em>advisory</em> — the system does not auto-apply them to activities or budgets.
 * Listeners use them to log the change and flag the project for re-baseline so the
 * planner can decide how to amend the plan.
 */
public record VariationOrderApprovedEvent(
    UUID voId,
    UUID contractId,
    UUID projectId,
    String voNumber,
    BigDecimal voValue,
    BigDecimal impactOnBudget,
    Integer impactOnScheduleDays
) {
}
