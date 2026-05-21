package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ActivitySubContractorAssignment;
import com.bipros.resource.domain.model.master.SubContractorMaster;

import java.time.Instant;
import java.util.UUID;

public record ActivitySubContractorAssignmentResponse(
    UUID id,
    UUID activityId,
    UUID projectId,
    UUID subContractorMasterId,
    String subContractorName,
    String subContractorCode,
    String subContractorLocation,
    Instant createdAt,
    Instant updatedAt) {

  public static ActivitySubContractorAssignmentResponse from(
      ActivitySubContractorAssignment a, SubContractorMaster master) {
    return new ActivitySubContractorAssignmentResponse(
        a.getId(),
        a.getActivityId(),
        a.getProjectId(),
        a.getSubContractorMasterId(),
        master == null ? null : master.getName(),
        master == null ? null : master.getCode(),
        master == null ? null : master.getLocation(),
        a.getCreatedAt(),
        a.getUpdatedAt());
  }
}
