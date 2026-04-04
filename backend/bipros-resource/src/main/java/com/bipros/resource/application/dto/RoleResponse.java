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
    BigDecimal defaultRate,
    Double defaultUnitsPerTime,
    Integer sortOrder,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy) {

  public static RoleResponse from(Role role) {
    return new RoleResponse(
        role.getId(),
        role.getCode(),
        role.getName(),
        role.getDescription(),
        role.getResourceType(),
        role.getDefaultRate(),
        role.getDefaultUnitsPerTime(),
        role.getSortOrder(),
        role.getCreatedAt(),
        role.getUpdatedAt(),
        role.getCreatedBy(),
        role.getUpdatedBy());
  }
}
