package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.rate.MaterialRateMaster;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MaterialRateMasterResponse(
    UUID id,
    UUID categoryId,
    String categoryCode,
    String categoryName,
    String specGrade,
    String unit,
    BigDecimal rate,
    Boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static MaterialRateMasterResponse of(
      MaterialRateMaster e, String categoryCode, String categoryName) {
    return new MaterialRateMasterResponse(
        e.getId(),
        e.getCategoryId(), categoryCode, categoryName,
        e.getSpecGrade(),
        e.getUnit(), e.getRate(), e.getActive(),
        e.getCreatedAt(), e.getUpdatedAt());
  }
}
