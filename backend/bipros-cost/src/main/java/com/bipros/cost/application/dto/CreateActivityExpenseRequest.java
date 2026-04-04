package com.bipros.cost.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateActivityExpenseRequest(
        @NotNull(message = "Activity ID is required")
        UUID activityId,

        @NotNull(message = "Project ID is required")
        UUID projectId,

        UUID costAccountId,

        @NotBlank(message = "Name is required")
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
}
