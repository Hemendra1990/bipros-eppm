package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.master.EmploymentTypeMaster;

import java.time.Instant;
import java.util.UUID;

public record EmploymentTypeMasterResponse(
    UUID id,
    String code,
    String name,
    String description,
    Integer sortOrder,
    Boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static EmploymentTypeMasterResponse from(EmploymentTypeMaster m) {
    return new EmploymentTypeMasterResponse(
        m.getId(),
        m.getCode(),
        m.getName(),
        m.getDescription(),
        m.getSortOrder(),
        m.getActive(),
        m.getCreatedAt(),
        m.getUpdatedAt());
  }
}
