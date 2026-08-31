package com.bipros.project.application.dto;

import com.bipros.project.domain.model.BoqStatus;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** All fields optional; null leaves the stored value unchanged. */
public record UpdateBoqItemRequest(
    @Size(max = 2000, message = "description must be at most 2000 characters")
    String description,
    String unit,
    UUID wbsNodeId,
    /**
     * Explicit unlink flag. {@code wbsNodeId=null} means "leave unchanged" (like every
     * other field), so clearing the WBS link needs this separate signal. When true,
     * {@code wbsNodeId} is ignored and the link is set to null.
     */
    Boolean clearWbsNode,
    @PositiveOrZero BigDecimal boqQty,
    @PositiveOrZero BigDecimal boqRate,
    @PositiveOrZero BigDecimal budgetedRate,
    @PositiveOrZero BigDecimal qtyExecutedToDate,
    @PositiveOrZero BigDecimal actualRate,
    @Size(max = 80) String chapter,
    BoqStatus status
) {}
