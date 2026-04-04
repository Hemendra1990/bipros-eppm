package com.bipros.document.application.dto;

import com.bipros.document.domain.model.DrawingDiscipline;
import com.bipros.document.domain.model.DrawingRegister;
import com.bipros.document.domain.model.DrawingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DrawingRegisterResponse(
    UUID id,
    UUID projectId,
    UUID documentId,
    String drawingNumber,
    String title,
    DrawingDiscipline discipline,
    String revision,
    LocalDate revisionDate,
    DrawingStatus status,
    String packageCode,
    String scale,
    Instant createdAt,
    Instant updatedAt
) {
    public static DrawingRegisterResponse from(DrawingRegister drawing) {
        return new DrawingRegisterResponse(
            drawing.getId(),
            drawing.getProjectId(),
            drawing.getDocumentId(),
            drawing.getDrawingNumber(),
            drawing.getTitle(),
            drawing.getDiscipline(),
            drawing.getRevision(),
            drawing.getRevisionDate(),
            drawing.getStatus(),
            drawing.getPackageCode(),
            drawing.getScale(),
            drawing.getCreatedAt(),
            drawing.getUpdatedAt()
        );
    }
}
