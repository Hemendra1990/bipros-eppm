package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceCategory;
import com.bipros.resource.domain.model.ResourceOwnership;
import com.bipros.resource.domain.model.ResourceStatus;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.ResourceUnit;
import com.bipros.resource.domain.model.UtilisationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ResourceResponse(
    UUID id,
    String code,
    String name,
    ResourceType resourceType,
    UUID resourceTypeDefId,
    String resourceTypeCode,
    String resourceTypeName,
    ResourceCategory resourceCategory,
    ResourceUnit unit,
    UUID parentId,
    UUID calendarId,
    String email,
    String phone,
    String title,
    Double maxUnitsPerDay,
    Double defaultUnitsPerTime,
    Double poolMaxAvailable,
    Double plannedUnitsToday,
    Double actualUnitsToday,
    Double utilisationPercent,
    UtilisationStatus utilisationStatus,
    BigDecimal dailyCostLakh,
    BigDecimal cumulativeCostCrores,
    String wbsAssignmentId,
    ResourceStatus status,
    Double hourlyRate,
    Double costPerUse,
    Double overtimeRate,
    Integer sortOrder,
    // ── Screen 04 Equipment Master fields ──
    String capacitySpec,
    String makeModel,
    Integer quantityAvailable,
    ResourceOwnership ownershipType,
    Double standardOutputPerDay,
    String standardOutputUnit,
    BigDecimal fuelLitresPerHour,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy) {

  public static ResourceResponse from(Resource resource) {
    Double utilisation = resource.getUtilisationPercent();
    if (utilisation == null
        && resource.getPlannedUnitsToday() != null
        && resource.getMaxUnitsPerDay() != null
        && resource.getMaxUnitsPerDay() > 0) {
      utilisation = (resource.getPlannedUnitsToday() / resource.getMaxUnitsPerDay()) * 100.0;
    }
    UtilisationStatus status = resource.getUtilisationStatus();
    // Keep the stored status, but override when it disagrees with the numeric bucket (OVER_90 is
    // only appropriate >= 90%, CRITICAL_100 only at >= 100%). Below 90%, fall back to ACTIVE.
    if (utilisation != null) {
      if (utilisation >= 100.0) {
        status = UtilisationStatus.CRITICAL_100;
      } else if (utilisation >= 90.0) {
        status = UtilisationStatus.OVER_90;
      } else if (status == UtilisationStatus.OVER_90 || status == UtilisationStatus.CRITICAL_100) {
        status = UtilisationStatus.ACTIVE;
      }
    }
    var def = resource.getResourceTypeDef();
    return new ResourceResponse(
        resource.getId(),
        resource.getCode(),
        resource.getName(),
        resource.getResourceType(),
        def == null ? null : def.getId(),
        def == null ? null : def.getCode(),
        def == null ? null : def.getName(),
        resource.getResourceCategory(),
        resource.getUnit(),
        resource.getParentId(),
        resource.getCalendarId(),
        resource.getEmail(),
        resource.getPhone(),
        resource.getTitle(),
        resource.getMaxUnitsPerDay(),
        resource.getDefaultUnitsPerTime(),
        resource.getPoolMaxAvailable(),
        resource.getPlannedUnitsToday(),
        resource.getActualUnitsToday(),
        utilisation,
        status,
        resource.getDailyCostLakh(),
        resource.getCumulativeCostCrores(),
        resource.getWbsAssignmentId(),
        resource.getStatus(),
        resource.getHourlyRate(),
        resource.getCostPerUse(),
        resource.getOvertimeRate(),
        resource.getSortOrder(),
        resource.getCapacitySpec(),
        resource.getMakeModel(),
        resource.getQuantityAvailable(),
        resource.getOwnershipType(),
        resource.getStandardOutputPerDay(),
        resource.getStandardOutputUnit(),
        resource.getFuelLitresPerHour(),
        resource.getCreatedAt(),
        resource.getUpdatedAt(),
        resource.getCreatedBy(),
        resource.getUpdatedBy());
  }
}
