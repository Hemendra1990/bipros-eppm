package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.LabourReturn;
import com.bipros.resource.domain.model.SkillCategory;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

public record LabourReturnResponse(
    UUID id,
    UUID projectId,
    String contractorName,
    LocalDate returnDate,
    SkillCategory skillCategory,
    Integer headCount,
    Double manDays,
    UUID wbsNodeId,
    String siteLocation,
    String remarks,
    Instant createdAt,
    String createdBy
) {
  public static LabourReturnResponse from(LabourReturn entity) {
    return new LabourReturnResponse(
        entity.getId(),
        entity.getProjectId(),
        entity.getContractorName(),
        entity.getReturnDate(),
        entity.getSkillCategory(),
        entity.getHeadCount(),
        entity.getManDays(),
        entity.getWbsNodeId(),
        entity.getSiteLocation(),
        entity.getRemarks(),
        entity.getCreatedAt(),
        entity.getCreatedBy());
  }
}
