package com.bipros.contract.application.dto;

import com.bipros.contract.domain.model.AttachmentEntityType;
import com.bipros.contract.domain.model.ContractAttachment;
import com.bipros.contract.domain.model.ContractAttachmentType;

import java.time.Instant;
import java.util.UUID;

public record ContractAttachmentResponse(
    UUID id,
    UUID projectId,
    UUID contractId,
    AttachmentEntityType entityType,
    UUID entityId,
    String fileName,
    long fileSize,
    String mimeType,
    ContractAttachmentType attachmentType,
    String description,
    String uploadedBy,
    Instant uploadedAt,
    Instant createdAt
) {
    public static ContractAttachmentResponse from(ContractAttachment a) {
        return new ContractAttachmentResponse(
            a.getId(),
            a.getProjectId(),
            a.getContractId(),
            a.getEntityType(),
            a.getEntityId(),
            a.getFileName(),
            a.getFileSize() == null ? 0 : a.getFileSize(),
            a.getMimeType(),
            a.getAttachmentType(),
            a.getDescription(),
            a.getUploadedBy(),
            a.getUploadedAt(),
            a.getCreatedAt()
        );
    }
}
