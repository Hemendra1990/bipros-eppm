package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.UnitRateCategory;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Unified Unit Rate Master create/update payload. Accepts both the original
 * {@code rateType}/{@code pricePerUnit} shape and the new Screen 05 fields
 * (budgeted/actual rates, effective range, approver, category). Legacy aliases
 * {@code standardRate}/{@code overtimeRate} are preserved for P6-style clients.
 */
public record CreateResourceRateRequest(
    @JsonAlias({"type"}) String rateType,
    @JsonAlias({"standardRate", "rate"})
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    BigDecimal pricePerUnit,
    BigDecimal overtimeRate,
    @NotNull(message = "Effective date is required") LocalDate effectiveDate,
    Double maxUnitsPerTime,

    // ── Screen 05 enrichment ─────────────────────────────────────────
    BigDecimal budgetedRate,
    BigDecimal actualRate,
    LocalDate effectiveTo,
    UnitRateCategory category,
    UUID approvedByUserId,
    String approvedByName) {

  /** Effective rate type, defaulting to STANDARD when the caller sent only {@code standardRate}. */
  public String effectiveRateType() {
    if (rateType != null && !rateType.isBlank()) return rateType;
    return overtimeRate != null && pricePerUnit == null ? "OVERTIME" : "STANDARD";
  }

  /** Effective price-per-unit; falls back to {@code budgetedRate} then {@code overtimeRate}. */
  public BigDecimal effectivePricePerUnit() {
    if (pricePerUnit != null) return pricePerUnit;
    if (budgetedRate != null) return budgetedRate;
    return overtimeRate;
  }
}
