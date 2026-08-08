package com.bipros.project.application.dto;

import com.bipros.project.domain.model.BoqOperation;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One operation of a split BOQ line — request shape (id/isLegacy/executedQty ignored on writes)
 * and response shape ({@code executedQty} = approved DPR sum attributed to the operation).
 */
public record BoqOperationDto(
    UUID id,
    String opCode,
    String name,
    String unit,
    BigDecimal targetQty,
    BigDecimal weightPct,
    Boolean isMeasure,
    Boolean isLegacy,
    Integer sortOrder,
    UUID workActivityId,
    BigDecimal executedQty
) {

  public static BoqOperationDto from(BoqOperation op, BigDecimal executedQty) {
    return new BoqOperationDto(
        op.getId(), op.getOpCode(), op.getName(), op.getUnit(), op.getTargetQty(),
        op.getWeightPct(), op.getIsMeasure(), op.getIsLegacy(), op.getSortOrder(),
        op.getWorkActivityId(), executedQty);
  }
}
