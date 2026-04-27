package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ProductivityNorm;
import com.bipros.resource.domain.model.ProductivityNormType;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceTypeDef;
import com.bipros.resource.domain.model.WorkActivity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductivityNormResponse(
    UUID id,
    ProductivityNormType normType,

    UUID workActivityId,
    String workActivityName,
    String workActivityCode,

    UUID resourceTypeDefId,
    String resourceTypeDefName,

    UUID resourceId,
    String resourceCode,
    String resourceName,

    @Schema(deprecated = true, description = "Deprecated echo of the legacy free-text activity label.")
    @Deprecated
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
    WorkActivity wa = norm.getWorkActivity();
    ResourceTypeDef def = norm.getResourceTypeDef();
    Resource res = norm.getResource();
    return new ProductivityNormResponse(
        norm.getId(),
        norm.getNormType(),
        wa == null ? null : wa.getId(),
        wa == null ? null : wa.getName(),
        wa == null ? null : wa.getCode(),
        def == null ? null : def.getId(),
        def == null ? null : def.getName(),
        res == null ? null : res.getId(),
        res == null ? null : res.getCode(),
        res == null ? null : res.getName(),
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
