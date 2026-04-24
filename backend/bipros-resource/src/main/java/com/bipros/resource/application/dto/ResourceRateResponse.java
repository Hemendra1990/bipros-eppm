package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceRate;
import com.bipros.resource.domain.model.UnitRateCategory;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Unit Rate Master row. {@link #variance} and {@link #variancePercent} are computed on read
 * (Actual − Budgeted) so the register grid can highlight over-budget lines in red (>5%).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceRateResponse(
    UUID id,
    UUID resourceId,
    String rateType,
    BigDecimal pricePerUnit,
    BigDecimal budgetedRate,
    BigDecimal actualRate,
    BigDecimal variance,
    BigDecimal variancePercent,
    LocalDate effectiveDate,
    LocalDate effectiveTo,
    UnitRateCategory category,
    UUID approvedByUserId,
    String approvedByName,
    Double maxUnitsPerTime,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy) {

  public static ResourceRateResponse from(ResourceRate rate) {
    BigDecimal budgeted = rate.getBudgetedRate() != null ? rate.getBudgetedRate() : rate.getPricePerUnit();
    BigDecimal actual = rate.getActualRate();
    BigDecimal variance = null;
    BigDecimal variancePercent = null;
    if (actual != null && budgeted != null) {
      variance = actual.subtract(budgeted);
      if (budgeted.signum() != 0) {
        variancePercent = variance
            .multiply(BigDecimal.valueOf(100))
            .divide(budgeted, 4, RoundingMode.HALF_UP);
      }
    }
    return new ResourceRateResponse(
        rate.getId(),
        rate.getResourceId(),
        rate.getRateType(),
        rate.getPricePerUnit(),
        budgeted,
        actual,
        variance,
        variancePercent,
        rate.getEffectiveDate(),
        rate.getEffectiveTo(),
        rate.getCategory(),
        rate.getApprovedByUserId(),
        rate.getApprovedByName(),
        rate.getMaxUnitsPerTime(),
        rate.getCreatedAt(),
        rate.getUpdatedAt(),
        rate.getCreatedBy(),
        rate.getUpdatedBy());
  }
}
