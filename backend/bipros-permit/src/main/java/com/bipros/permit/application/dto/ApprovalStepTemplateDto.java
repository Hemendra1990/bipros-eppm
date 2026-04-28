package com.bipros.permit.application.dto;

import java.util.UUID;

public record ApprovalStepTemplateDto(
        UUID id,
        UUID permitTypeTemplateId,
        int stepNo,
        String label,
        String role,
        String requiredForRiskLevels,
        boolean optional
) {}
