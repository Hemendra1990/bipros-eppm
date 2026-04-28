package com.bipros.analytics.infrastructure.llm;

import java.time.Duration;
import java.util.List;

public record CompletionRequest(
    String systemPrompt,
    List<Message> conversation,
    int maxTokens,
    Duration timeout
) {}
