package com.bipros.project.application.dto;

import com.bipros.project.domain.model.StretchStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload for POST /v1/projects/{projectId}/stretches (Screen 06). The {@code stretchCode}
 * is optional — the service auto-generates {@code STR-NNN} when blank. Chainages are in metres.
 */
public record CreateStretchRequest(
    @Size(max = 30) String stretchCode,
    @Size(max = 200) String name,
    @NotNull @Min(0) Long fromChainageM,
    @NotNull @Min(0) Long toChainageM,
    UUID assignedSupervisorId,
    @Size(max = 60) String packageCode,
    StretchStatus status,
    @Size(max = 200) String milestoneName,
    LocalDate targetDate,
    /** Optional list of BOQ item IDs to link on create. */
    List<UUID> boqItemIds
) {
}
