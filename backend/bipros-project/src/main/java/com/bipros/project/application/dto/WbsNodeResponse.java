package com.bipros.project.application.dto;

import com.bipros.project.domain.model.WbsPhase;
import com.bipros.project.domain.model.WbsStatus;
import com.bipros.project.domain.model.WbsType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WbsNodeResponse(
    UUID id,
    String code,
    String name,
    UUID parentId,
    UUID projectId,
    UUID obsNodeId,
    Integer sortOrder,
    Double summaryDuration,
    Double summaryPercentComplete,
    Integer wbsLevel,
    WbsType wbsType,
    WbsPhase phase,
    WbsStatus wbsStatus,
    UUID responsibleOrganisationId,
    LocalDate plannedStart,
    LocalDate plannedFinish,
    BigDecimal budgetCrores,
    String gisPolygonId,
    List<WbsNodeResponse> children
) {
}
