package com.bipros.project.application.dto;

import com.bipros.project.domain.model.BoqStatus;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** All fields optional; null leaves the stored value unchanged. */
public record UpdateBoqItemRequest(
    String description,
    String unit,
    UUID wbsNodeId,
    @PositiveOrZero BigDecimal boqQty,
    @PositiveOrZero BigDecimal boqRate,
    @PositiveOrZero BigDecimal budgetedRate,
    @PositiveOrZero BigDecimal qtyExecutedToDate,
    @PositiveOrZero BigDecimal actualRate,
    @Size(max = 80) String chapter,
    BoqStatus status
) {}
