package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.MaterialCategoryMaster;

import java.time.Instant;
import java.util.UUID;

public record MaterialCategoryMasterResponse(
    UUID id,
    String code,
    String name,
    String description,
    Integer sortOrder,
    Boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static MaterialCategoryMasterResponse from(MaterialCategoryMaster e) {
    return new MaterialCategoryMasterResponse(
        e.getId(),
        e.getCode(),
        e.getName(),
        e.getDescription(),
        e.getSortOrder(),
        e.getActive(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}
