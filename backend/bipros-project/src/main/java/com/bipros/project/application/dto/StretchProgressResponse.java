package com.bipros.project.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Weighted progress rollup for a {@link com.bipros.project.domain.model.Stretch}. Computed from
 * the BOQ items linked via {@code StretchActivityLink}: the weighted % complete is
 * {@code Σ(qtyExecuted × boqRate) / Σ(boqQty × boqRate)}, which is the standard construction
 * industry "cost-weighted physical progress" so a partly-complete expensive item contributes
 * proportionately more than a fully-complete cheap item.
 */
public record StretchProgressResponse(
    UUID stretchId,
    String stretchCode,
    String stretchName,
    int linkedBoqItemCount,
    BigDecimal totalBoqAmount,
    BigDecimal totalExecutedAmount,
    BigDecimal percentComplete
) {
}
