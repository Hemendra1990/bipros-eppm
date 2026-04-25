package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.Role;
import com.bipros.resource.domain.model.ResourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RoleResponse(
    UUID id,
    String code,
    String name,
    String description,
    ResourceType resourceType,
    UUID resourceTypeDefId,
    String resourceTypeCode,
    String resourceTypeName,
    BigDecimal defaultRate,
    Double defaultUnitsPerTime,
    Integer sortOrder,
    String rateUnit,
    BigDecimal budgetedRate,
    BigDecimal actualRate,
    BigDecimal rateVariance,
    BigDecimal rateVariancePercent,
    String rateRemarks,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy) {

  public static RoleResponse from(Role role) {
    BigDecimal budgeted = role.getBudgetedRate();
    BigDecimal actual = role.getActualRate();
    BigDecimal variance = null;
    BigDecimal variancePct = null;
    if (budgeted != null && actual != null) {
      variance = actual.subtract(budgeted);
      if (budgeted.signum() != 0) {
        variancePct = variance.divide(budgeted, 6, java.math.RoundingMode.HALF_UP);
      }
    }
    var def = role.getResourceTypeDef();
    return new RoleResponse(
        role.getId(),
        role.getCode(),
        role.getName(),
        role.getDescription(),
        role.getResourceType(),
        def == null ? null : def.getId(),
        def == null ? null : def.getCode(),
        def == null ? null : def.getName(),
        role.getDefaultRate(),
        role.getDefaultUnitsPerTime(),
        role.getSortOrder(),
        role.getRateUnit(),
        budgeted,
        actual,
        variance,
        variancePct,
        role.getRateRemarks(),
        role.getCreatedAt(),
        role.getUpdatedAt(),
        role.getCreatedBy(),
        role.getUpdatedBy());
  }
}
