package com.bipros.gis.application.dto;

import com.bipros.gis.domain.model.ConstructionProgressSnapshot;
import com.bipros.gis.domain.model.ProgressAnalysisMethod;
import com.bipros.gis.domain.model.SatelliteAlertFlag;

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
    Double aiProgressPercent,
    Double cvi,
    Double edi,
    Double ndviChange,
    String wbsPackageCode,
    SatelliteAlertFlag alertFlag,
    ProgressAnalysisMethod analysisMethod,
    String analyzerId,
    Integer analysisDurationMs,
    Long analysisCostMicros,
    String remarks,
    Instant createdAt,
    String createdBy
) {
    public static ConstructionProgressResponse from(ConstructionProgressSnapshot e) {
        return new ConstructionProgressResponse(
            e.getId(),
            e.getProjectId(),
            e.getWbsPolygonId(),
            e.getCaptureDate(),
            e.getSatelliteImageId(),
            e.getDerivedProgressPercent(),
            e.getContractorClaimedPercent(),
            e.getVariancePercent(),
            e.getAiProgressPercent(),
            e.getCvi(),
            e.getEdi(),
            e.getNdviChange(),
            e.getWbsPackageCode(),
            e.getAlertFlag(),
            e.getAnalysisMethod(),
            e.getAnalyzerId(),
            e.getAnalysisDurationMs(),
            e.getAnalysisCostMicros(),
            e.getRemarks(),
            e.getCreatedAt(),
            e.getCreatedBy()
        );
    }
}
