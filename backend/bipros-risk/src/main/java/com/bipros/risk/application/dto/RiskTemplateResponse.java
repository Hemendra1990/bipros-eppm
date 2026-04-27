package com.bipros.risk.application.dto;

import com.bipros.risk.application.service.RiskCategoryMasterService;
import com.bipros.risk.domain.model.Industry;
import com.bipros.risk.domain.model.RiskTemplate;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RiskTemplateResponse(
    UUID id,
    String code,
    String title,
    String description,
    Industry industry,
    Set<String> applicableProjectCategories,
    /** Embedded category summary (null if uncategorised). */
    RiskCategorySummaryDto category,
    Integer defaultProbability,
    Integer defaultImpactCost,
    Integer defaultImpactSchedule,
    String mitigationGuidance,
    Boolean isOpportunity,
    Integer sortOrder,
    Boolean active,
    Boolean systemDefault,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy) {

    public static RiskTemplateResponse from(RiskTemplate t) {
        return new RiskTemplateResponse(
            t.getId(),
            t.getCode(),
            t.getTitle(),
            t.getDescription(),
            t.getIndustry(),
            t.getApplicableProjectCategories(),
            RiskCategoryMasterService.toSummary(t.getCategory()),
            t.getDefaultProbability(),
            t.getDefaultImpactCost(),
            t.getDefaultImpactSchedule(),
            t.getMitigationGuidance(),
            t.getIsOpportunity(),
            t.getSortOrder(),
            t.getActive(),
            t.getSystemDefault(),
            t.getCreatedAt(),
            t.getUpdatedAt(),
            t.getCreatedBy(),
            t.getUpdatedBy());
    }
}
