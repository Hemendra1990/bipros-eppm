package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.LabourCategory;

public record LabourCategoryReference(
    LabourCategory category,
    String codePrefix,
    String displayName
) {}
