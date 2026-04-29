package com.bipros.cost.application.dto;

import com.bipros.cost.domain.entity.BudgetChangeLog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BudgetChangeLogResponse(
    UUID id,
    UUID projectId,
    UUID fromWbsNodeId,
    String fromWbsNodeCode,
    UUID toWbsNodeId,
    String toWbsNodeCode,
    BigDecimal amount,
    BudgetChangeLog.ChangeType changeType,
    BudgetChangeLog.ChangeStatus status,
    String reason,
    UUID requestedBy,
    String requestedByName,
    UUID decidedBy,
    String decidedByName,
    Instant requestedAt,
    Instant decidedAt
) {
}
