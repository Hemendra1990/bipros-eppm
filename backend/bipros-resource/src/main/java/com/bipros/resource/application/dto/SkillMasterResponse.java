package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.master.SkillMaster;

import java.time.Instant;
import java.util.UUID;

public record SkillMasterResponse(
    UUID id,
    String code,
    String name,
    String description,
    Integer sortOrder,
    Boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static SkillMasterResponse from(SkillMaster m) {
    return new SkillMasterResponse(
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
