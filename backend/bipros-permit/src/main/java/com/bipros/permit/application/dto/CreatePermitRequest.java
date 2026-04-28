package com.bipros.permit.application.dto;

import com.bipros.permit.domain.model.RiskLevel;
import com.bipros.permit.domain.model.WorkShift;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreatePermitRequest(
        @NotNull UUID permitTypeTemplateId,
        @NotNull RiskLevel riskLevel,
        UUID contractorOrgId,
        String supervisorName,
        @Size(max = 200) String locationZone,
        @Size(max = 60) String chainageMarker,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotNull WorkShift shift,
        @Size(max = 4000) String taskDescription,
        @NotEmpty @Valid List<PermitWorkerDto> workers,
        List<UUID> confirmedPpeItemIds,
        boolean declarationAccepted,
        String customFieldsJson
) {}
