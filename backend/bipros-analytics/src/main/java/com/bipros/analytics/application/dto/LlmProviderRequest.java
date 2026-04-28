package com.bipros.analytics.application.dto;

import com.bipros.analytics.domain.model.LlmProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LlmProviderRequest(
    @NotNull LlmProvider provider,
    @NotBlank @Size(max = 128) String modelName,
    @NotBlank @Size(max = 128) String displayName,
    String apiKey,
    @Size(max = 512) String endpointOverride,
    boolean isDefault
) {}
