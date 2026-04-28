package com.bipros.analytics.infrastructure.llm;

import com.bipros.analytics.domain.model.LlmProvider;

/**
 * Provider-agnostic LLM completion port. Phase 0 needs only the simple completion path
 * (used for "Test connection" pings). Phase 2 will extend with tool-calling.
 *
 * Implementations are stateless beans selected by {@link LlmProvider}. They receive
 * the resolved API key + endpoint at call time, so a single adapter bean serves
 * every user of that provider.
 */
public interface LlmAdapter {

    LlmProvider provider();

    /**
     * @param req       the completion request
     * @param apiKey    decrypted API key (may be null for OLLAMA local)
     * @param endpoint  endpoint override (may be null → use provider default)
     * @param model     model identifier (e.g. "claude-sonnet-4-6")
     */
    CompletionResponse complete(CompletionRequest req, String apiKey, String endpoint, String model);
}
