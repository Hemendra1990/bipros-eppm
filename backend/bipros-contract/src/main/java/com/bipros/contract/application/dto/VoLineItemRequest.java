package com.bipros.contract.application.dto;

import com.bipros.common.event.VoLineItemAction;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

// Validation that boqItemId is set for REVISE_*/DELETE_ITEM and that newItemNo is set for
// ADD_ITEM lives in VariationOrderService.assertLineItemConsistency.
public record VoLineItemRequest(
    UUID id,
    @NotNull VoLineItemAction action,
    UUID boqItemId,
    String newItemNo,
    String newItemDescription,
    String newItemUnit,
    BigDecimal revisedQty,
    BigDecimal revisedRate,
    BigDecimal lineImpactAmount
) {}
