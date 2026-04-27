package com.bipros.risk.application.dto;

import java.util.UUID;

public record RiskCategoryTypeResponse(
    UUID id,
    String code,
    String name,
    String description,
    Boolean active,
    Integer sortOrder,
    Boolean systemDefault,
    Long childCount
) {}
