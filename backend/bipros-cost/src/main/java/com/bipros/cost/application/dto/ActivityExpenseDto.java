package com.bipros.cost.application.dto;

import com.bipros.cost.domain.entity.ActivityExpense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ActivityExpenseDto(
        UUID id,
        UUID activityId,
        UUID projectId,
        UUID costAccountId,
        String name,
        String description,
        String expenseCategory,
        BigDecimal budgetedCost,
        BigDecimal actualCost,
        BigDecimal remainingCost,
        BigDecimal atCompletionCost,
        Double percentComplete,
        LocalDate plannedStartDate,
        LocalDate plannedFinishDate,
        LocalDate actualStartDate,
        LocalDate actualFinishDate
) {
    public static ActivityExpenseDto from(ActivityExpense entity) {
        return new ActivityExpenseDto(
                entity.getId(),
                entity.getActivityId(),
                entity.getProjectId(),
                entity.getCostAccountId(),
                entity.getName(),
                entity.getDescription(),
                entity.getExpenseCategory(),
                entity.getBudgetedCost(),
                entity.getActualCost(),
                entity.getRemainingCost(),
                entity.getAtCompletionCost(),
                entity.getPercentComplete(),
                entity.getPlannedStartDate(),
                entity.getPlannedFinishDate(),
                entity.getActualStartDate(),
                entity.getActualFinishDate()
        );
    }
}
