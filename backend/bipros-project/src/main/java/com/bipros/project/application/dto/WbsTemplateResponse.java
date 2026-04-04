package com.bipros.project.application.dto;

import com.bipros.project.domain.model.AssetClass;

import java.time.Instant;
import java.util.UUID;

public record WbsTemplateResponse(
    UUID id,
    String code,
    String name,
    AssetClass assetClass,
    String description,
    String defaultStructure,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {

    public static WbsTemplateResponse from(com.bipros.project.domain.model.WbsTemplate template) {
        return new WbsTemplateResponse(
            template.getId(),
            template.getCode(),
            template.getName(),
            template.getAssetClass(),
            template.getDescription(),
            template.getDefaultStructure(),
            template.getIsActive(),
            template.getCreatedAt(),
            template.getUpdatedAt()
        );
    }
}
