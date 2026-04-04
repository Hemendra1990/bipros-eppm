package com.bipros.cost.application.dto;

import com.bipros.cost.domain.entity.CostAccount;

import java.util.UUID;

public record CostAccountDto(
        UUID id,
        String code,
        String name,
        String description,
        UUID parentId,
        Integer sortOrder
) {
    public static CostAccountDto from(CostAccount entity) {
        return new CostAccountDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getParentId(),
                entity.getSortOrder()
        );
    }
}
