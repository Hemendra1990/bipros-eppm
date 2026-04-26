package com.bipros.cost.application.dto;

import com.bipros.common.web.json.Views;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.fasterxml.jackson.annotation.JsonView;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ActivityExpenseDto(
        UUID id,
        UUID projectId,
        UUID activityId,
        String description,
        @JsonView(Views.FinanceConfidential.class) BigDecimal amount,
        String currency,
        LocalDate expenseDate,
        String category,
        Instant createdAt,
        Instant updatedAt
) {
    public static ActivityExpenseDto from(ActivityExpense entity) {
        return new ActivityExpenseDto(
                entity.getId(),
                entity.getProjectId(),
                entity.getActivityId(),
                entity.getDescription(),
                entity.getActualCost(),
                entity.getCurrency(),
                entity.getActualStartDate(),
                entity.getExpenseCategory(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
