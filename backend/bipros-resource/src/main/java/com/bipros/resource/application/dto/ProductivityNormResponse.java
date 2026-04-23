package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ProductivityNorm;
import com.bipros.resource.domain.model.ProductivityNormType;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductivityNormResponse(
    UUID id,
    ProductivityNormType normType,
    String activityName,
    String unit,
    BigDecimal outputPerManPerDay,
    BigDecimal outputPerHour,
    Integer crewSize,
    BigDecimal outputPerDay,
    Double workingHoursPerDay,
    BigDecimal fuelLitresPerHour,
    String equipmentSpec,
    String remarks
) {
  public static ProductivityNormResponse from(ProductivityNorm norm) {
    return new ProductivityNormResponse(
        norm.getId(),
        norm.getNormType(),
        norm.getActivityName(),
        norm.getUnit(),
        norm.getOutputPerManPerDay(),
        norm.getOutputPerHour(),
        norm.getCrewSize(),
        norm.getOutputPerDay(),
        norm.getWorkingHoursPerDay(),
        norm.getFuelLitresPerHour(),
        norm.getEquipmentSpec(),
        norm.getRemarks()
    );
  }
}
