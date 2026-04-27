package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.Industry;

import java.util.UUID;

/** Compact projection of {@code RiskCategoryMaster} embedded in Risk / RiskTemplate responses. */
public record RiskCategorySummaryDto(
    UUID id,
    String code,
    String name,
    Industry industry,
    RiskCategoryTypeSummary type
) {}
