package com.bipros.cost.application.dto;

import com.bipros.cost.domain.entity.FinancialPeriod;

import java.time.LocalDate;
import java.util.UUID;

public record FinancialPeriodDto(
        UUID id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String periodType,
        Boolean isClosed,
        Integer sortOrder
) {
    public static FinancialPeriodDto from(FinancialPeriod entity) {
        return new FinancialPeriodDto(
                entity.getId(),
                entity.getName(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getPeriodType(),
                entity.getIsClosed(),
                entity.getSortOrder()
        );
    }
}
