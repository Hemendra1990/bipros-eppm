package com.bipros.resource.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LabourMasterDashboardSummary(
    UUID projectId,
    Integer totalDesignations,
    Integer totalWorkforce,
    BigDecimal dailyPayroll,
    String currency,
    Integer skillCategoryCount,
    NationalityMix nationalityMix,
    List<LabourCategorySummary> byCategory
) {
    public record NationalityMix(Integer omani, Integer expat, Integer omaniOrExpat) {}
}
