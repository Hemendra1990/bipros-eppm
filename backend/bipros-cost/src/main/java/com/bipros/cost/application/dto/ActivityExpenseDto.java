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
        String name,
        String description,
        String expenseCategory,
        @JsonView(Views.FinanceConfidential.class) BigDecimal budgetedCost,
        @JsonView(Views.FinanceConfidential.class) BigDecimal actualCost,
        @JsonView(Views.FinanceConfidential.class) BigDecimal remainingCost,
        @JsonView(Views.FinanceConfidential.class) BigDecimal atCompletionCost,
        Double percentComplete,
        String currency,
        LocalDate plannedStartDate,
        LocalDate plannedFinishDate,
        LocalDate actualStartDate,
        LocalDate actualFinishDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static ActivityExpenseDto from(ActivityExpense entity) {
        return new ActivityExpenseDto(
                entity.getId(),
                entity.getProjectId(),
                entity.getActivityId(),
                entity.getName(),
                entity.getDescription(),
                entity.getExpenseCategory(),
                entity.getBudgetedCost(),
                entity.getActualCost(),
                entity.getRemainingCost(),
                entity.getAtCompletionCost(),
                entity.getPercentComplete(),
                entity.getCurrency(),
                entity.getPlannedStartDate(),
                entity.getPlannedFinishDate(),
                entity.getActualStartDate(),
                entity.getActualFinishDate(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
