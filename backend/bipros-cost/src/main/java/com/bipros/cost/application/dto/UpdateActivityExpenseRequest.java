package com.bipros.cost.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateActivityExpenseRequest(
        UUID costAccountId,

        String name,

        String description,

        String expenseCategory,

        String category,

        BigDecimal budgetedCost,

        BigDecimal actualCost,

        BigDecimal amount,

        BigDecimal remainingCost,

        BigDecimal atCompletionCost,

        Double percentComplete,

        String currency,

        LocalDate plannedStartDate,

        LocalDate plannedFinishDate,

        LocalDate actualStartDate,

        LocalDate actualFinishDate,

        LocalDate expenseDate
) {
}
