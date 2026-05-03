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

    record ChatRequest(java.util.List<Message> messages,
                       java.util.List<ToolSpec> tools,
                       Integer maxTokens,
                       Double temperature,
                       Long timeoutMs,
                       JsonNode responseFormat,
                       java.util.List<DocumentInput> documents,
                       String reasoningEffort) {
        // Back-compat: 5-arg, 6-arg constructors used by existing callers.
        public ChatRequest(java.util.List<Message> messages, java.util.List<ToolSpec> tools, Integer maxTokens,
                           Double temperature, Long timeoutMs) {
            this(messages, tools, maxTokens, temperature, timeoutMs, null, null, null);
        }
        public ChatRequest(java.util.List<Message> messages, java.util.List<ToolSpec> tools, Integer maxTokens,
                           Double temperature, Long timeoutMs, JsonNode responseFormat) {
            this(messages, tools, maxTokens, temperature, timeoutMs, responseFormat, null, null);
        }
    }

    record Message(String role, String content, String imageUrl,
                   String toolCallId, java.util.List<ToolCall> toolCalls) {
        public Message(String role, String content) {
            this(role, content, null, null, null);
        }
        public Message(String role, String content, String imageUrl) {
            this(role, content, imageUrl, null, null);
        }
        /** Assistant turn that issued tool calls. {@code content} may be empty. */
        public static Message assistantWithToolCalls(String content, java.util.List<ToolCall> toolCalls) {
            return new Message("assistant", content == null ? "" : content, null, null, toolCalls);
        }
        /** Tool result keyed back to the assistant's tool_call by id. */
        public static Message toolResult(String toolCallId, String content) {
            return new Message("tool", content, null, toolCallId, null);
        }
    }

    /**
     * A binary document attached to the user message. Two transport modes:
     * <ul>
     *   <li><b>Inline</b> — set {@code data} to the raw bytes; the provider
     *       base64-encodes them into a {@code file_data} content block. Best
     *       for files up to ~5 MB.</li>
     *   <li><b>By reference</b> — set {@code fileId} to a {@code file-…} id
     *       returned by a prior {@code POST /v1/files} upload; the provider
     *       sends a {@code file_id} content block. Best for files >5 MB.</li>
     * </ul>
     * Exactly one of {@code data} / {@code fileId} should be non-null.
     */
    record DocumentInput(String filename, String mimeType, byte[] data, String fileId) {
        public DocumentInput(String filename, String mimeType, byte[] data) {
            this(filename, mimeType, data, null);
        }
        public static DocumentInput byReference(String filename, String mimeType, String fileId) {
            return new DocumentInput(filename, mimeType, null, fileId);
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

    /**
     * One streaming tool-call fragment. {@code index} keys the call across the
     * stream — a single tool_call's {@code arguments} usually arrives as many
     * deltas, each carrying only the same {@code index} (id and name typically
     * appear once on the first delta). The orchestrator accumulates by index.
     */
    record ToolCallDelta(int index, String id, String name, String argumentsDelta) {
    }
}
