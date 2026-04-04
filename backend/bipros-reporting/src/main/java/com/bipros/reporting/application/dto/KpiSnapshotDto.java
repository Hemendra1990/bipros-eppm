package com.bipros.reporting.application.dto;

import com.bipros.reporting.domain.model.KpiSnapshot;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class KpiSnapshotDto {
    private UUID id;
    private UUID kpiDefinitionId;
    private UUID projectId;
    private String period;
    private Double value;
    private String status;
    private Instant calculatedAt;

    public static KpiSnapshotDto from(KpiSnapshot entity) {
        return KpiSnapshotDto.builder()
                .id(entity.getId())
                .kpiDefinitionId(entity.getKpiDefinitionId())
                .projectId(entity.getProjectId())
                .period(entity.getPeriod())
                .value(entity.getValue())
                .status(entity.getStatus() != null ? entity.getStatus().toString() : null)
                .calculatedAt(entity.getCalculatedAt())
                .build();
    }
}
