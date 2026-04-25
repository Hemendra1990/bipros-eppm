package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.Industry;
import com.bipros.risk.domain.model.RiskCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Update payload. {@code code} and {@code industry} are accepted from clients but the
 * service rejects mutations to either field on system-default rows.
 */
public record UpdateRiskTemplateRequest(
    @NotBlank(message = "Code is required")
    @Size(max = 50, message = "Code must be at most 50 characters")
    String code,

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be at most 255 characters")
    String title,

    String description,

    Industry industry,

    Set<String> applicableProjectCategories,

    RiskCategory category,

    @Min(value = 1, message = "defaultProbability must be 1-5")
    @Max(value = 5, message = "defaultProbability must be 1-5")
    Integer defaultProbability,

    @Min(value = 1, message = "defaultImpactCost must be 1-5")
    @Max(value = 5, message = "defaultImpactCost must be 1-5")
    Integer defaultImpactCost,

    @Min(value = 1, message = "defaultImpactSchedule must be 1-5")
    @Max(value = 5, message = "defaultImpactSchedule must be 1-5")
    Integer defaultImpactSchedule,

    String mitigationGuidance,

    Boolean isOpportunity,

    Integer sortOrder,

    Boolean active
) {}
