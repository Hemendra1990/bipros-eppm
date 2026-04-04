package com.bipros.document.application.dto;

import com.bipros.document.domain.model.RfiPriority;
import com.bipros.document.domain.model.RfiRegister;
import com.bipros.document.domain.model.RfiStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RfiRegisterResponse(
    UUID id,
    UUID projectId,
    String rfiNumber,
    String subject,
    String description,
    String raisedBy,
    String assignedTo,
    LocalDate raisedDate,
    LocalDate dueDate,
    LocalDate closedDate,
    RfiStatus status,
    RfiPriority priority,
    String response,
    Instant createdAt,
    Instant updatedAt
) {
    public static RfiRegisterResponse from(RfiRegister rfi) {
        return new RfiRegisterResponse(
            rfi.getId(),
            rfi.getProjectId(),
            rfi.getRfiNumber(),
            rfi.getSubject(),
            rfi.getDescription(),
            rfi.getRaisedBy(),
            rfi.getAssignedTo(),
            rfi.getRaisedDate(),
            rfi.getDueDate(),
            rfi.getClosedDate(),
            rfi.getStatus(),
            rfi.getPriority(),
            rfi.getResponse(),
            rfi.getCreatedAt(),
            rfi.getUpdatedAt()
        );
    }
}
