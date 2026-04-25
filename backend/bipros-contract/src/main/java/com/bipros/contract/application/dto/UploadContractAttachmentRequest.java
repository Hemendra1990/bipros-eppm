package com.bipros.contract.application.dto;

import com.bipros.contract.domain.model.ContractAttachmentType;
import jakarta.validation.constraints.NotNull;

public record UploadContractAttachmentRequest(
    @NotNull ContractAttachmentType attachmentType,
    String description
) {}
