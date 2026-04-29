package com.bipros.cost.application.dto;

import com.bipros.cost.domain.entity.BudgetChangeLog;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateBudgetChangeRequest(
    UUID fromWbsNodeId,

    UUID toWbsNodeId,

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    BigDecimal amount,

    @NotNull(message = "Change type is required")
    BudgetChangeLog.ChangeType changeType,

    @NotNull(message = "Reason is required")
    String reason
) {
}
