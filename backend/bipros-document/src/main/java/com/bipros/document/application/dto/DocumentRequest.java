package com.bipros.document.application.dto;

import com.bipros.document.domain.model.DocumentStatus;
import com.bipros.document.domain.model.DocumentType;
import com.bipros.document.domain.model.DrawingDiscipline;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record DocumentRequest(
    @NotNull(message = "Folder ID is required")
    UUID folderId,

    @NotBlank(message = "Document number is required")
    String documentNumber,

    @NotBlank(message = "Document title is required")
    String title,

    String description,

    @NotBlank(message = "File name is required")
    String fileName,

    @NotNull(message = "File size is required")
    Long fileSize,

    @NotBlank(message = "MIME type is required")
    String mimeType,

    @NotBlank(message = "File path is required")
    String filePath,

    DocumentStatus status,

    DocumentType documentType,

    DrawingDiscipline discipline,

    String transmittalNumber,

    String wbsPackageCode,

    String issuedBy,

    LocalDate issuedDate,

    String approvedBy,

    LocalDate approvedDate,

    String tags
) {
}
