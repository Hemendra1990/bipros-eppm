package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceType;

import java.time.Instant;
import java.util.UUID;

public record ResourceTypeResponse(
    UUID id,
    String code,
    String name,
    String description,
    Integer sortOrder,
    Boolean active,
    Boolean systemDefault,
    Instant createdAt,
    Instant updatedAt) {

  public static ResourceTypeResponse from(ResourceType e) {
    return new ResourceTypeResponse(
        e.getId(),
        e.getCode(),
        e.getName(),
        e.getDescription(),
        e.getSortOrder(),
        e.getActive(),
        e.getSystemDefault(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}
