package com.bipros.cost.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateFinancialPeriodRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        String periodType,

        Integer sortOrder
) {
}
