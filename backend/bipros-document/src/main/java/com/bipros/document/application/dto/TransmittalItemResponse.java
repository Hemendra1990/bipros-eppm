package com.bipros.document.application.dto;

import com.bipros.document.domain.model.TransmittalItem;
import com.bipros.document.domain.model.TransmittalPurpose;

import java.util.UUID;

public record TransmittalItemResponse(
    UUID id,
    UUID transmittalId,
    UUID documentId,
    TransmittalPurpose purpose,
    String remarks
) {
    public static TransmittalItemResponse from(TransmittalItem item) {
        return new TransmittalItemResponse(
            item.getId(),
            item.getTransmittalId(),
            item.getDocumentId(),
            item.getPurpose(),
            item.getRemarks()
        );
    }
}
