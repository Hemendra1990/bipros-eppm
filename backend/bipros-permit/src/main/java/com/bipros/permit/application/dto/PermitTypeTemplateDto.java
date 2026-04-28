package com.bipros.permit.application.dto;

import com.bipros.permit.domain.model.NightWorkPolicy;
import com.bipros.permit.domain.model.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record PermitTypeTemplateDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @NotNull RiskLevel defaultRiskLevel,
        boolean jsaRequired,
        boolean gasTestRequired,
        boolean isolationRequired,
        boolean blastingRequired,
        boolean divingRequired,
        @NotNull NightWorkPolicy nightWorkPolicy,
        @Positive int maxDurationHours,
        String minApprovalRole,
        String colorHex,
        String iconKey,
        int sortOrder
) {}
