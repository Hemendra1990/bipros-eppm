package com.bipros.risk.application.dto;

import java.util.UUID;

/** Compact projection of {@code RiskCategoryType} embedded in category responses. */
public record RiskCategoryTypeSummary(
    UUID id,
    String code,
    String name
) {}
