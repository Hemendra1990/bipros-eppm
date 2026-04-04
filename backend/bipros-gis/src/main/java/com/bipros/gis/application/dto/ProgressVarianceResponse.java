package com.bipros.gis.application.dto;

import java.util.UUID;

public record ProgressVarianceResponse(
    UUID wbsPolygonId,
    String wbsCode,
    String wbsName,
    Double derivedPercent,
    Double claimedPercent,
    Double variancePercent,
    String varianceStatus
) {}
