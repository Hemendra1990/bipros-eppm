package com.bipros.scheduling.application.dto;

import com.bipros.scheduling.domain.model.SchedulingOption;

import java.util.UUID;

/**
 * {@code projectId} is taken from the URL path, so the body only needs {@code option}. The field
 * is kept for backwards compatibility with callers that still include it; it is ignored when the
 * path parameter is supplied.
 */
public record ScheduleRequest(
    UUID projectId,
    SchedulingOption option
) {
}
