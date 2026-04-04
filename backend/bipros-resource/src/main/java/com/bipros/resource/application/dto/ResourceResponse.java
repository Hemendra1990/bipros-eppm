package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceStatus;
import com.bipros.resource.domain.model.ResourceType;

import java.time.Instant;
import java.util.UUID;

public record ResourceResponse(
    UUID id,
    String code,
    String name,
    ResourceType resourceType,
    UUID parentId,
    UUID calendarId,
    String email,
    String phone,
    String title,
    Double maxUnitsPerDay,
    Double defaultUnitsPerTime,
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
        resource.getParentId(),
        resource.getCalendarId(),
        resource.getEmail(),
        resource.getPhone(),
        resource.getTitle(),
        resource.getMaxUnitsPerDay(),
        resource.getDefaultUnitsPerTime(),
        resource.getStatus(),
        resource.getSortOrder(),
        resource.getCreatedAt(),
        resource.getUpdatedAt(),
        resource.getCreatedBy(),
        resource.getUpdatedBy());
  }
}
