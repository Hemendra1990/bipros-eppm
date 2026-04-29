package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.LabourCategory;

import java.math.BigDecimal;

public record LabourCategorySummary(
    LabourCategory category,
    String categoryDisplay,
    String codePrefix,
    Integer designationCount,
    Integer workerCount,
    BigDecimal dailyCost,
    String gradeRange,
    String dailyRateRange,
    String keyRolesSummary
) {}
