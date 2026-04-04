package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.SkillCategory;

public record DeploymentSummary(
    SkillCategory skillCategory,
    Integer totalHeadCount,
    Double totalManDays
) {}
