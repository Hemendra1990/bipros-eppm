package com.bipros.document.application.dto;

import com.bipros.document.domain.model.DocumentVersion;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersionResponse(
    UUID id,
    UUID documentId,
    Integer versionNumber,
    String fileName,
    String filePath,
    Long fileSize,
    String changeDescription,
    String uploadedBy,
    Instant uploadedAt
) {
    public static DocumentVersionResponse from(DocumentVersion version) {
        return new DocumentVersionResponse(
            version.getId(),
            version.getDocumentId(),
            version.getVersionNumber(),
            version.getFileName(),
            version.getFilePath(),
            version.getFileSize(),
            version.getChangeDescription(),
            version.getUploadedBy(),
            version.getUploadedAt()
        );
    }
}
