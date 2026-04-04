package com.bipros.document.application.dto;

import com.bipros.document.domain.model.DocumentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DocumentFolderRequest(
    @NotBlank(message = "Folder name is required")
    String name,

    @NotBlank(message = "Folder code is required")
    String code,

    DocumentCategory category,

    UUID parentId,

    UUID wbsNodeId,

    Integer sortOrder
) {
}
