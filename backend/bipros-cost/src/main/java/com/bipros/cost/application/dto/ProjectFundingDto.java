package com.bipros.cost.application.dto;

import com.bipros.cost.domain.entity.ProjectFunding;

import java.math.BigDecimal;
import java.util.UUID;

public record ProjectFundingDto(
        UUID id,
        UUID projectId,
        UUID fundingSourceId,
        UUID wbsNodeId,
        BigDecimal allocatedAmount
) {
    public static ProjectFundingDto from(ProjectFunding entity) {
        return new ProjectFundingDto(
                entity.getId(),
                entity.getProjectId(),
                entity.getFundingSourceId(),
                entity.getWbsNodeId(),
                entity.getAllocatedAmount()
        );
    }
}
