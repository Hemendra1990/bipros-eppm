package com.bipros.document.application.dto;

import com.bipros.document.domain.model.DocumentStatus;
import com.bipros.document.domain.model.DocumentType;
import com.bipros.document.domain.model.DrawingDiscipline;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Metadata part of a multipart document upload. The {@code MultipartFile} supplies
 * fileName / size / mime type / binary — they are intentionally absent here.
 */
public record UploadDocumentRequest(
        @NotNull(message = "Folder ID is required")
        UUID folderId,

        @NotBlank(message = "Document number is required")
        String documentNumber,

        @NotBlank(message = "Document title is required")
        String title,

        String description,

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
