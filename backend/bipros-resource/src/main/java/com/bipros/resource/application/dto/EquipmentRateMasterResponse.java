package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.rate.EquipmentRateMaster;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EquipmentRateMasterResponse(
    UUID id,
    String equipmentName,
    String make,
    String model,
    String unit,
    BigDecimal rate,
    Boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static EquipmentRateMasterResponse from(EquipmentRateMaster e) {
    return new EquipmentRateMasterResponse(
        e.getId(),
        e.getEquipmentName(), e.getMake(), e.getModel(),
        e.getUnit(), e.getRate(), e.getActive(),
        e.getCreatedAt(), e.getUpdatedAt());
  }
}
