package com.bipros.common.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Snapshot of a single VO line item carried inside {@link VariationOrderApprovedEvent}. The
 * BOQ-mutation listener consumes this without touching the contract module's entities.
 *
 * <p>Field semantics depend on {@link #action}:
 * <ul>
 *   <li>{@code ADD_ITEM}: {@code boqItemId} null; {@code newItemNo / newItemDescription /
 *       newItemUnit / revisedQty / revisedRate} populated.</li>
 *   <li>{@code REVISE_QTY}: {@code boqItemId} non-null; {@code revisedQty} populated.</li>
 *   <li>{@code REVISE_RATE}: {@code boqItemId} non-null; {@code revisedRate} populated.</li>
 *   <li>{@code DELETE_ITEM}: {@code boqItemId} non-null; everything else may be null.</li>
 * </ul>
 */
public record VoLineItemPayload(
    UUID lineItemId,
    VoLineItemAction action,
    UUID boqItemId,
    String newItemNo,
    String newItemDescription,
    String newItemUnit,
    BigDecimal revisedQty,
    BigDecimal revisedRate,
    BigDecimal lineImpactAmount
) {
}
