package com.bipros.ai.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Adapter that exposes OpenAiCompatibleProvider through the LlmProvider interface.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleLlmProvider implements LlmProvider {

    private final OpenAiCompatibleProvider delegate;
    private final LlmProviderConfigRepository repository;

    @Override
    public ChatResponse chatCompletion(ChatRequest req) {
        LlmProviderConfig config = resolveDefault();
        return delegate.chat(config, req);
    }

    @Override
    public Flux<ChatChunk> chatCompletionStream(ChatRequest req) {
        LlmProviderConfig config = resolveDefault();
        return delegate.chatStream(config, req);
    }

    @Override
    public boolean supportsTools() {
        return true;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String providerKey() {
        return "openai-compatible";
    }

    private LlmProviderConfig resolveDefault() {
        return repository.findByIsDefaultTrue()
                .orElseThrow(() -> new IllegalStateException("No default LLM provider configured"));
    }
}
