package com.bipros.document.application.dto;

import com.bipros.document.domain.model.DocumentCategory;
import com.bipros.document.domain.model.DocumentFolder;

import java.util.UUID;

public record DocumentFolderResponse(
    UUID id,
    UUID projectId,
    String name,
    String code,
    DocumentCategory category,
    UUID parentId,
    UUID wbsNodeId,
    Integer sortOrder
) {
    public static DocumentFolderResponse from(DocumentFolder folder) {
        return new DocumentFolderResponse(
            folder.getId(),
            folder.getProjectId(),
            folder.getName(),
            folder.getCode(),
            folder.getCategory(),
            folder.getParentId(),
            folder.getWbsNodeId(),
            folder.getSortOrder()
        );
    }
}
