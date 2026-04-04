package com.bipros.document.application.dto;

import com.bipros.document.domain.model.Transmittal;
import com.bipros.document.domain.model.TransmittalStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransmittalResponse(
    UUID id,
    UUID projectId,
    String transmittalNumber,
    String subject,
    String fromParty,
    String toParty,
    LocalDate sentDate,
    LocalDate dueDate,
    TransmittalStatus status,
    String remarks,
    Instant createdAt,
    Instant updatedAt
) {
    public static TransmittalResponse from(Transmittal transmittal) {
        return new TransmittalResponse(
            transmittal.getId(),
            transmittal.getProjectId(),
            transmittal.getTransmittalNumber(),
            transmittal.getSubject(),
            transmittal.getFromParty(),
            transmittal.getToParty(),
            transmittal.getSentDate(),
            transmittal.getDueDate(),
            transmittal.getStatus(),
            transmittal.getRemarks(),
            transmittal.getCreatedAt(),
            transmittal.getUpdatedAt()
        );
    }
}
