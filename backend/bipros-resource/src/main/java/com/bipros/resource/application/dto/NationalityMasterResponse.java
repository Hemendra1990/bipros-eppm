package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.master.NationalityMaster;

import java.time.Instant;
import java.util.UUID;

public record NationalityMasterResponse(
    UUID id,
    String code,
    String name,
    String description,
    Integer sortOrder,
    Boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static NationalityMasterResponse from(NationalityMaster m) {
    return new NationalityMasterResponse(
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
