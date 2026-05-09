package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.GradeMaster;

import java.time.Instant;
import java.util.UUID;

public record GradeMasterResponse(
    UUID id,
    String code,
    String name,
    String description,
    Integer sortOrder,
    Boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static GradeMasterResponse from(GradeMaster e) {
    return new GradeMasterResponse(
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
