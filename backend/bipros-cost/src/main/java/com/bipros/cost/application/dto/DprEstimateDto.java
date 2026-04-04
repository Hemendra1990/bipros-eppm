package com.bipros.cost.application.dto;

import com.bipros.cost.domain.entity.DprEstimate;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class DprEstimateDto {
    private UUID id;
    private UUID projectId;
    private UUID wbsNodeId;
    private String costCategory;
    private BigDecimal estimatedAmount;
    private BigDecimal revisedAmount;
    private String remarks;

    public static DprEstimateDto from(DprEstimate entity) {
        return DprEstimateDto.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .wbsNodeId(entity.getWbsNodeId())
                .costCategory(entity.getCostCategory().toString())
                .estimatedAmount(entity.getEstimatedAmount())
                .revisedAmount(entity.getRevisedAmount())
                .remarks(entity.getRemarks())
                .build();
    }
}
