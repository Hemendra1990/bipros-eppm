package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.UserResourceRole;

import java.time.LocalDate;
import java.util.UUID;

public record UserResourceRoleResponse(
    UUID id,
    UUID userId,
    UUID resourceRoleId,
    LocalDate assignedFrom,
    LocalDate assignedTo,
    String remarks
) {
  public static UserResourceRoleResponse from(UserResourceRole u) {
    return new UserResourceRoleResponse(
        u.getId(),
        u.getUserId(),
        u.getResourceRoleId(),
        u.getAssignedFrom(),
        u.getAssignedTo(),
        u.getRemarks()
    );
  }
}
