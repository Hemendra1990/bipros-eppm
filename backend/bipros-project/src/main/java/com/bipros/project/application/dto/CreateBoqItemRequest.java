package com.bipros.project.application.dto;

import com.bipros.project.domain.model.BoqStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

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
    @PositiveOrZero BigDecimal actualRate,

    /** MoRTH chapter grouping. Optional — auto-classifier may populate later. */
    @Size(max = 80) String chapter,

    /** Optional initial status. When omitted the service derives it from progress. */
    BoqStatus status
) {}
