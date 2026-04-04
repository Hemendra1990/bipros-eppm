package com.bipros.document.application.dto;

import com.bipros.document.domain.model.TransmittalPurpose;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransmittalItemRequest(
    @NotNull(message = "Document ID is required")
    UUID documentId,

    @NotNull(message = "Purpose is required")
    TransmittalPurpose purpose,

    String remarks
) {
}
