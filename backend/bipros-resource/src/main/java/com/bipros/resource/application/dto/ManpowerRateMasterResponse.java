package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.rate.ManpowerRateMaster;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ManpowerRateMasterResponse(
    UUID id,
    UUID roleId,
    String roleCode,
    String roleName,
    UUID categoryId,
    String categoryName,
    UUID gradeId,
    String gradeCode,
    String gradeName,
    String unit,
    BigDecimal rate,
    Boolean active,
    Instant createdAt,
    Instant updatedAt) {

  /**
   * Build a response with all FK names expanded. Names are passed in by the service so this
   * record stays a pure projection (no DB calls in {@code from}).
   */
  public static ManpowerRateMasterResponse of(
      ManpowerRateMaster e,
      String roleCode, String roleName,
      String categoryName,
      String gradeCode, String gradeName) {
    return new ManpowerRateMasterResponse(
        e.getId(),
        e.getRoleId(), roleCode, roleName,
        e.getCategoryId(), categoryName,
        e.getGradeId(), gradeCode, gradeName,
        e.getUnit(), e.getRate(), e.getActive(),
        e.getCreatedAt(), e.getUpdatedAt());
  }
}
