package com.bipros.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Flux;

/**
 * Abstraction over any OpenAI-compatible LLM provider.
 */
public interface LlmProvider {

    ChatResponse chatCompletion(ChatRequest req);

    Flux<ChatChunk> chatCompletionStream(ChatRequest req);

    boolean supportsTools();

    boolean supportsStreaming();

    String providerKey();

    record ChatRequest(java.util.List<Message> messages, java.util.List<ToolSpec> tools, Integer maxTokens,
                       Double temperature, Long timeoutMs, JsonNode responseFormat) {
        public ChatRequest(java.util.List<Message> messages, java.util.List<ToolSpec> tools, Integer maxTokens,
                           Double temperature, Long timeoutMs) {
            this(messages, tools, maxTokens, temperature, timeoutMs, null);
        }
    }

    record Message(String role, String content, String imageUrl) {
        public Message(String role, String content) {
            this(role, content, null);
        }
    }

    record ToolSpec(String name, String description, JsonNode parameters) {
    }

    record ChatResponse(String content, java.util.List<ToolCall> toolCalls, Usage usage, String model) {
    }

    record ToolCall(String id, String name, JsonNode arguments) {
    }

    record Usage(int promptTokens, int completionTokens, int totalTokens) {
    }

    record ChatChunk(String delta, ToolCallDelta toolCallDelta, String finishReason) {
    }

    record ToolCallDelta(String id, String name, String argumentsDelta) {
    }
}
