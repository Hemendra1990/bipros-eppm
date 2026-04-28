package com.bipros.analytics.infrastructure.llm;

public record TokenUsage(int inputTokens, int outputTokens) {
    public static TokenUsage empty() { return new TokenUsage(0, 0); }
}
