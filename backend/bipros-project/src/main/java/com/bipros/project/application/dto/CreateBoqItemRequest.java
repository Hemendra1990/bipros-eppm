package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateBoqItemRequest(
    @NotBlank(message = "itemNo is required")
    String itemNo,

    @NotBlank(message = "description is required")
    String description,

    @NotBlank(message = "unit is required")
    String unit,

    UUID wbsNodeId,

    @PositiveOrZero BigDecimal boqQty,
    @PositiveOrZero BigDecimal boqRate,
    @PositiveOrZero BigDecimal budgetedRate,
    @PositiveOrZero BigDecimal qtyExecutedToDate,
    @PositiveOrZero BigDecimal actualRate
) {}
