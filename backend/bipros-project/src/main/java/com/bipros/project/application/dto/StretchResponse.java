package com.bipros.project.application.dto;

import com.bipros.project.domain.model.Stretch;
import com.bipros.project.domain.model.StretchStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StretchResponse(
    UUID id,
    UUID projectId,
    String stretchCode,
    String name,
    Long fromChainageM,
    Long toChainageM,
    Long lengthM,
    UUID assignedSupervisorId,
    String packageCode,
    StretchStatus status,
    String milestoneName,
    LocalDate targetDate,
    List<UUID> boqItemIds
) {
    public static StretchResponse from(Stretch s, List<UUID> boqItemIds) {
        return new StretchResponse(
            s.getId(),
            s.getProjectId(),
            s.getStretchCode(),
            s.getName(),
            s.getFromChainageM(),
            s.getToChainageM(),
            s.getLengthM(),
            s.getAssignedSupervisorId(),
            s.getPackageCode(),
            s.getStatus(),
            s.getMilestoneName(),
            s.getTargetDate(),
            boqItemIds != null ? boqItemIds : List.of()
        );
    }
}
