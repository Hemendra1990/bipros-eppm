package com.bipros.analytics.application.dto;

import com.bipros.analytics.domain.model.LlmProvider;
import com.bipros.analytics.domain.model.ProviderStatus;

import java.time.Instant;
import java.util.UUID;

public record LlmProviderResponse(
    UUID id,
    LlmProvider provider,
    String modelName,
    String displayName,
    String endpointOverride,
    boolean isDefault,
    boolean apiKeyConfigured,
    ProviderStatus status,
    Instant lastValidatedAt,
    String lastValidationError,
    Instant createdAt,
    Instant updatedAt
) {}
