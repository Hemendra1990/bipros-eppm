package com.bipros.permit.application.dto;

import com.bipros.permit.domain.model.PermitStatus;
import com.bipros.permit.domain.model.RiskLevel;
import com.bipros.permit.domain.model.WorkShift;

import java.time.Instant;
import java.util.UUID;

/** Compact projection for list views (Permit Register, Recent Activity feed). */
public record PermitSummary(
        UUID id,
        String permitCode,
        UUID projectId,
        UUID permitTypeTemplateId,
        String permitTypeCode,
        String permitTypeName,
        String permitTypeColorHex,
        String permitTypeIconKey,
        PermitStatus status,
        RiskLevel riskLevel,
        WorkShift shift,
        String workDescription,
        String principalWorkerName,
        String principalWorkerNationality,
        Instant startAt,
        Instant endAt
) {}
