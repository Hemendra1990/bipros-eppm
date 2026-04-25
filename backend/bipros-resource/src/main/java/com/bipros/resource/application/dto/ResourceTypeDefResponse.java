package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.ResourceTypeDef;

import java.time.Instant;
import java.util.UUID;

public record ResourceTypeDefResponse(
    UUID id,
    String code,
    String name,
    ResourceType baseCategory,
    String codePrefix,
    Integer sortOrder,
    Boolean active,
    Boolean systemDefault,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy) {

  public static ResourceTypeDefResponse from(ResourceTypeDef def) {
    return new ResourceTypeDefResponse(
        def.getId(),
        def.getCode(),
        def.getName(),
        def.getBaseCategory(),
        def.getCodePrefix(),
        def.getSortOrder(),
        def.getActive(),
        def.getSystemDefault(),
        def.getCreatedAt(),
        def.getUpdatedAt(),
        def.getCreatedBy(),
        def.getUpdatedBy());
  }
}
