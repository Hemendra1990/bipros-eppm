package com.bipros.document.application.dto;

import com.bipros.document.domain.model.Document;
import com.bipros.document.domain.model.DocumentStatus;

import java.time.Instant;
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
            document.getTags(),
            document.getCreatedAt(),
            document.getUpdatedAt()
        );
    }
}
