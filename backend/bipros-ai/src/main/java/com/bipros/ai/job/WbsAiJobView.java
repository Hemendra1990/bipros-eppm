package com.bipros.ai.job;

import com.bipros.ai.wbs.dto.WbsAiGenerationResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * Public DTO returned by GET /v1/projects/{id}/wbs/ai/jobs/{id}. Strips
 * internal-only fields (file_sha256, raw metadata, etc) and parses
 * {@code resultJson} into the typed {@link WbsAiGenerationResponse} so the
 * frontend can switch to its existing preview shape with no extra work.
 */
public record WbsAiJobView(
        UUID id,
        UUID projectId,
        WbsAiJobStatus status,
        String progressStage,
        Integer progressPct,
        WbsAiGenerationResponse result,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
}
