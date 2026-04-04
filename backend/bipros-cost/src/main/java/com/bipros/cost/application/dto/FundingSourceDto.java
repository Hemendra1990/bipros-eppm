package com.bipros.cost.application.dto;

import com.bipros.cost.domain.entity.FundingSource;

import java.math.BigDecimal;
import java.util.UUID;

public record FundingSourceDto(
        UUID id,
        String name,
        String description,
        String code,
        BigDecimal totalAmount,
        BigDecimal allocatedAmount,
        BigDecimal remainingAmount
) {
    public static FundingSourceDto from(FundingSource entity) {
        return new FundingSourceDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCode(),
                entity.getTotalAmount(),
                entity.getAllocatedAmount(),
                entity.getRemainingAmount()
        );
    }
}
