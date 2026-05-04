package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.master.ManpowerCategoryMaster;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for {@link ManpowerCategoryMaster}. {@code parentName} is denormalised so the
 * admin table can show "Sub-Category of …" without a second lookup; populated by the service.
 */
public record ManpowerCategoryMasterResponse(
    UUID id,
    String code,
    String name,
    String description,
    UUID parentId,
    String parentName,
    Integer sortOrder,
    Boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static ManpowerCategoryMasterResponse from(
      ManpowerCategoryMaster m, String parentName) {
    return new ManpowerCategoryMasterResponse(
        m.getId(),
        m.getCode(),
        m.getName(),
        m.getDescription(),
        m.getParentId(),
        parentName,
        m.getSortOrder(),
        m.getActive(),
        m.getCreatedAt(),
        m.getUpdatedAt());
  }
}
