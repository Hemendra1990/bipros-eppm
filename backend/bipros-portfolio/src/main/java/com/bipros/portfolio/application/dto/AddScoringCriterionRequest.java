package com.bipros.portfolio.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddScoringCriterionRequest(
    @NotBlank(message = "Criterion name is required") String name,
    @NotNull(message = "Weight is required") Double weight,
    Double minScore,
    Double maxScore,
    @NotNull(message = "Sort order is required") Integer sortOrder) {}
