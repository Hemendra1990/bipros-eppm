package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceCategory;
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
    Integer sortOrder,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy) {

  public static ResourceResponse from(Resource resource) {
    return new ResourceResponse(
        resource.getId(),
        resource.getCode(),
        resource.getName(),
        resource.getResourceType(),
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
        resource.getUtilisationPercent(),
        resource.getUtilisationStatus(),
        resource.getDailyCostLakh(),
        resource.getCumulativeCostCrores(),
        resource.getWbsAssignmentId(),
        resource.getStatus(),
        resource.getSortOrder(),
        resource.getCreatedAt(),
        resource.getUpdatedAt(),
        resource.getCreatedBy(),
        resource.getUpdatedBy());
  }
}
