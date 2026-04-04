package com.bipros.gis.application.dto;

import com.bipros.gis.domain.model.ProgressAnalysisMethod;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ConstructionProgressRequest(
    @NotNull(message = "WBS Polygon ID is required")
    UUID wbsPolygonId,

    @NotNull(message = "Capture date is required")
    LocalDate captureDate,

    UUID satelliteImageId,

    Double derivedProgressPercent,

    Double contractorClaimedPercent,

    @NotNull(message = "Analysis method is required")
    ProgressAnalysisMethod analysisMethod,

    String remarks
) {}
