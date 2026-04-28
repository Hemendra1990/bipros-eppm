package com.bipros.permit.application.dto;

import com.bipros.permit.domain.model.RiskLevel;
import com.bipros.permit.domain.model.WorkShift;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Used for editing a permit while it is still in DRAFT. */
public record UpdatePermitRequest(
        UUID permitTypeTemplateId,
        RiskLevel riskLevel,
        UUID contractorOrgId,
        String supervisorName,
        @Size(max = 200) String locationZone,
        @Size(max = 60) String chainageMarker,
        Instant startAt,
        Instant endAt,
        WorkShift shift,
        @Size(max = 4000) String taskDescription,
        @Valid List<PermitWorkerDto> workers,
        List<UUID> confirmedPpeItemIds,
        String customFieldsJson
) {}
