package com.bipros.cost.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateActivityExpenseRequest(
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
