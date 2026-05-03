package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.master.SkillLevelMaster;

import java.time.Instant;
import java.util.UUID;

public record SkillLevelMasterResponse(
    UUID id,
    String code,
    String name,
    String description,
    Integer sortOrder,
    Boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static SkillLevelMasterResponse from(SkillLevelMaster m) {
    return new SkillLevelMasterResponse(
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
