package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceRate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ResourceRateResponse(
    UUID id,
    UUID resourceId,
    String rateType,
    BigDecimal pricePerUnit,
    LocalDate effectiveDate,
    Double maxUnitsPerTime,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy) {

  public static ResourceRateResponse from(ResourceRate rate) {
    return new ResourceRateResponse(
        rate.getId(),
        rate.getResourceId(),
        rate.getRateType(),
        rate.getPricePerUnit(),
        rate.getEffectiveDate(),
        rate.getMaxUnitsPerTime(),
        rate.getCreatedAt(),
        rate.getUpdatedAt(),
        rate.getCreatedBy(),
        rate.getUpdatedBy());
  }
}
