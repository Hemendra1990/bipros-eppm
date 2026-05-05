package com.bipros.contract.application.dto;

import com.bipros.common.event.VoLineItemAction;
import com.bipros.contract.domain.model.VoLineItem;

import java.math.BigDecimal;
import java.util.UUID;

public record VoLineItemResponse(
    UUID id,
    UUID variationOrderId,
    VoLineItemAction action,
    UUID boqItemId,
    String newItemNo,
    String newItemDescription,
    String newItemUnit,
    BigDecimal revisedQty,
    BigDecimal revisedRate,
    BigDecimal lineImpactAmount
) {
  public static VoLineItemResponse from(VoLineItem li) {
    return new VoLineItemResponse(
        li.getId(),
        li.getVariationOrderId(),
        li.getAction(),
        li.getBoqItemId(),
        li.getNewItemNo(),
        li.getNewItemDescription(),
        li.getNewItemUnit(),
        li.getRevisedQty(),
        li.getRevisedRate(),
        li.getLineImpactAmount()
    );
  }
}
