package com.bipros.project.application.dto;

import com.bipros.project.domain.model.DprAttachment;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight projection of {@link DprAttachment} returned to the frontend. The binary itself
 * is fetched from {@code GET /v1/projects/{projectId}/dpr/{dprId}/photos/{photoId}}; the URL is
 * built client-side from {@code id} + the parent ids.
 */
public record DprAttachmentResponse(
    UUID id,
    UUID dprId,
    String fileName,
    String mimeType,
    Long fileSize,
    String caption,
    Instant capturedAt,
    Instant createdAt
) {
  public static DprAttachmentResponse from(DprAttachment a) {
    return new DprAttachmentResponse(
        a.getId(),
        a.getDprId(),
        a.getFileName(),
        a.getMimeType(),
        a.getFileSize(),
        a.getCaption(),
        a.getCapturedAt(),
        a.getCreatedAt()
    );
  }
}
