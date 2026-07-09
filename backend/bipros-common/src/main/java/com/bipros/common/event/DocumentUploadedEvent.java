package com.bipros.common.event;

import java.util.UUID;

/**
 * Published when a document is uploaded/versioned. Consumed by the Document Intelligence agent
 * trigger path (wiring the publisher into DocumentService is a follow-up; the nightly sweep is the
 * safe fallback until then).
 */
public record DocumentUploadedEvent(
    UUID projectId,
    UUID documentId
) {
}
