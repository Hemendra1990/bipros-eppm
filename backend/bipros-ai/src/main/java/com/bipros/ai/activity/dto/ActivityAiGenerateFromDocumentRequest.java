package com.bipros.ai.activity.dto;

/**
 * Metadata for the from-document activity-generation flow. Mirrors
 * {@link com.bipros.ai.wbs.dto.WbsAiGenerateFromDocumentRequest}: the document
 * itself is uploaded as a separate multipart part; this record carries only
 * the small structured hints.
 */
public record ActivityAiGenerateFromDocumentRequest(
        Integer targetActivityCount,
        Double defaultDurationDays
) {
}
