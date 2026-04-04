package com.bipros.document.application.dto;

import com.bipros.document.domain.model.DocumentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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

    String tags
) {
}
