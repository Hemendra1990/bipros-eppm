package com.bipros.gis.application.dto;

import com.bipros.gis.domain.model.ConstructionProgressSnapshot;
import com.bipros.gis.domain.model.ProgressAnalysisMethod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ConstructionProgressResponse(
    UUID id,
    UUID projectId,
    UUID wbsPolygonId,
    LocalDate captureDate,
    UUID satelliteImageId,
    Double derivedProgressPercent,
    Double contractorClaimedPercent,
    Double variancePercent,
    ProgressAnalysisMethod analysisMethod,
    String remarks,
    Instant createdAt,
    String createdBy
) {
    public static ConstructionProgressResponse from(ConstructionProgressSnapshot entity) {
        return new ConstructionProgressResponse(
            entity.getId(),
            entity.getProjectId(),
            entity.getWbsPolygonId(),
            entity.getCaptureDate(),
            entity.getSatelliteImageId(),
            entity.getDerivedProgressPercent(),
            entity.getContractorClaimedPercent(),
            entity.getVariancePercent(),
            entity.getAnalysisMethod(),
            entity.getRemarks(),
            entity.getCreatedAt(),
            entity.getCreatedBy()
        );
    }
}
