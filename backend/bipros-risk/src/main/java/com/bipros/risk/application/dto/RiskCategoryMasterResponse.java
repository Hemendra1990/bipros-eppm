package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.Industry;

import java.util.UUID;

public record RiskCategoryMasterResponse(
    UUID id,
    String code,
    String name,
    String description,
    Industry industry,
    RiskCategoryTypeSummary type,
    Boolean active,
    Integer sortOrder,
    Boolean systemDefault
) {}
