package com.bipros.document.application.dto;

import com.bipros.document.domain.model.Document;
import com.bipros.document.domain.model.DocumentStatus;
import com.bipros.document.domain.model.DocumentType;
import com.bipros.document.domain.model.DrawingDiscipline;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    UUID folderId,
    UUID projectId,
    String documentNumber,
    String title,
    String description,
    String fileName,
    Long fileSize,
    String mimeType,
    String filePath,
    Integer currentVersion,
    DocumentStatus status,
    DocumentType documentType,
    DrawingDiscipline discipline,
    String transmittalNumber,
    String wbsPackageCode,
    String issuedBy,
    LocalDate issuedDate,
    String approvedBy,
    LocalDate approvedDate,
    String tags,
    Instant createdAt,
    Instant updatedAt
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
            document.getId(),
            document.getFolderId(),
            document.getProjectId(),
            document.getDocumentNumber(),
            document.getTitle(),
            document.getDescription(),
            document.getFileName(),
            document.getFileSize(),
            document.getMimeType(),
            document.getFilePath(),
            document.getCurrentVersion(),
            document.getStatus(),
            document.getDocumentType(),
            document.getDiscipline(),
            document.getTransmittalNumber(),
            document.getWbsPackageCode(),
            document.getIssuedBy(),
            document.getIssuedDate(),
            document.getApprovedBy(),
            document.getApprovedDate(),
            document.getTags(),
            document.getCreatedAt(),
            document.getUpdatedAt()
        );
    }
}
