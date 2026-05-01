package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.model.ResourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ResourceRoleResponse(
    UUID id,
    String code,
    String name,
    String description,
    UUID resourceTypeId,
    String resourceTypeCode,
    String resourceTypeName,
    String productivityUnit,
    BigDecimal defaultRate,
    Integer sortOrder,
    Boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static ResourceRoleResponse from(ResourceRole r) {
    ResourceType type = r.getResourceType();
    return new ResourceRoleResponse(
        r.getId(),
        r.getCode(),
        r.getName(),
        r.getDescription(),
        type == null ? null : type.getId(),
        type == null ? null : type.getCode(),
        type == null ? null : type.getName(),
        r.getProductivityUnit(),
        r.getDefaultRate(),
        r.getSortOrder(),
        r.getActive(),
        r.getCreatedAt(),
        r.getUpdatedAt());
  }
}
