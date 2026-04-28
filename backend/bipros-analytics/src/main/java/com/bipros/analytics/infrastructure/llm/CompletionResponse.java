package com.bipros.analytics.infrastructure.llm;

public record CompletionResponse(
    String text,
    TokenUsage tokens
) {}
