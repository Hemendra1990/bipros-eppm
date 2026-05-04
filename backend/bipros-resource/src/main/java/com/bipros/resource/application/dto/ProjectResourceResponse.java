package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ProjectResource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProjectResourceResponse(
    UUID id,
    UUID projectId,
    UUID resourceId,
    String resourceCode,
    String resourceName,
    String resourceTypeName,
    String roleName,
    BigDecimal masterRate,
    BigDecimal rateOverride,
    Double availabilityOverride,
    String customUnit,
    String notes,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy
) {

    public static ProjectResourceResponse from(
            ProjectResource pr,
            String resourceCode,
            String resourceName,
            String resourceTypeName,
            String roleName,
            BigDecimal masterRate) {
        return new ProjectResourceResponse(
            pr.getId(),
            pr.getProjectId(),
            pr.getResourceId(),
            resourceCode,
            resourceName,
            resourceTypeName,
            roleName,
            masterRate,
            pr.getRateOverride(),
            pr.getAvailabilityOverride(),
            pr.getCustomUnit(),
            pr.getNotes(),
            pr.getCreatedAt(),
            pr.getUpdatedAt(),
            pr.getCreatedBy(),
            pr.getUpdatedBy()
        );
    }
}
