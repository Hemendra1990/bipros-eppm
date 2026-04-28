package com.bipros.analytics.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AnalyticsAssistantRequest(
        @NotBlank @Size(max = 4000) String queryText,
        UUID projectContext
) {}
