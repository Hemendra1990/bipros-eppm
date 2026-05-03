package com.bipros.ai.provider;

import com.bipros.ai.provider.crypto.ApiKeyCipher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI-compatible provider implementation using WebClient.
 * Supports BEARER, API_KEY, and AZURE auth schemes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleProvider {

    private final ApiKeyCipher apiKeyCipher;
    private final ModelCapabilityRegistry capabilityRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmProvider.ChatResponse chat(LlmProviderConfig config, LlmProvider.ChatRequest req) {
        // Multimodal inputs (PDFs, images, audio) are only supported by OpenAI's
        // Responses API today. When the request carries documents and the provider
        // is OpenAI, force the Responses path even if the operator configured a
        // /v1 (chat) base URL.
        ApiFlavor flavor = detectFlavor(config);
        if (req.documents() != null && !req.documents().isEmpty() && isOpenAiBaseUrl(config.getBaseUrl())) {
            flavor = ApiFlavor.RESPONSES;
        }
        long effectiveTimeoutMs = effectiveTimeoutMs(config, req);
        WebClient client = buildClient(effectiveTimeoutMs);
        String url = resolveUrl(config, flavor);
        String apiKey = decryptKey(config);

        Object body;
        if (flavor == ApiFlavor.RESPONSES) {
            OpenAiResponsesRequest r = toResponsesRequest(req);
            r.model = config.getModel();
            // With the resolved model in hand, validate / downgrade reasoning_effort
            // against the model's accepted vocabulary. Prevents 400s from
            // model-specific enums that don't match the universal "low" we send.
            if (r.reasoning != null) {
                String resolved = capabilityRegistry.resolveReasoningEffort(
                        r.model, r.reasoning.effort());
                r.reasoning = resolved == null ? null : new OpenAiResponsesReasoning(resolved);
            }
            body = r;
        } else {
            // Documents on a non-OpenAI provider: callers fall back to upstream
            // text extraction. Reject here to make the contract clear instead of
            // silently dropping the file.
            if (req.documents() != null && !req.documents().isEmpty()) {
                throw new RuntimeException("Native document input is not supported by this provider. " +
                        "Configure an OpenAI provider (https://api.openai.com/v1) or upstream text extraction.");
            }
            OpenAiChatRequest r = toOpenAiRequest(req);
            r.model = config.getModel();
            body = r;
        }

        String responseJson = postWithRetry(client, url, config, apiKey, body, effectiveTimeoutMs);

        return flavor == ApiFlavor.RESPONSES
                ? parseResponsesResponse(responseJson)
                : parseChatResponse(responseJson);
    }

    /**
     * POST with retry-on-transient: 429 / 500 / 502 / 503 / 504 retry with
     * exponential backoff + jitter, capped at {@value #MAX_RETRY_ATTEMPTS}
     * attempts. 4xx other than 429 are non-retryable (bad request, auth, etc).
     */
    private String postWithRetry(WebClient client, String url, LlmProviderConfig config,
                                  String apiKey, Object body, long timeoutMs) {
        int attempt = 0;
        WebClientResponseException last = null;
        while (true) {
            attempt++;
            try {
                return client.post()
                        .uri(url)
                        .headers(h -> applyAuth(h, config, apiKey))
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofMillis(timeoutMs));
            } catch (WebClientResponseException e) {
                last = e;
                if (!isRetryable(e) || attempt >= MAX_RETRY_ATTEMPTS) {
                    throw new RuntimeException(formatHttpError(e, url), e);
                }
                long delay = backoffMillis(e, attempt);
                log.warn("LLM call to {} returned {} (attempt {}/{}); retrying in {} ms",
                        url, e.getStatusCode().value(), attempt, MAX_RETRY_ATTEMPTS, delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(formatHttpError(last, url), last);
                }
            }
        }
    }

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private static boolean isRetryable(WebClientResponseException e) {
        int status = e.getStatusCode().value();
        return status == 429 || (status >= 500 && status <= 599);
    }

    /** Exponential backoff with jitter; honors Retry-After header if the server sent one. */
    private static long backoffMillis(WebClientResponseException e, int attempt) {
        String retryAfter = e.getHeaders().getFirst(org.springframework.http.HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                long sec = Long.parseLong(retryAfter.trim());
                if (sec > 0 && sec <= 60) return sec * 1000L;
            } catch (NumberFormatException ignored) {
                // value may be an HTTP date; fall through to exponential backoff.
            }
        }
        long base = (long) Math.pow(2, attempt - 1) * 1000L;
        long jitter = java.util.concurrent.ThreadLocalRandom.current().nextLong(500);
        return Math.min(base + jitter, 10_000L);
    }

    private String formatHttpError(WebClientResponseException e, String url) {
        String body = e.getResponseBodyAsString();
        String detail = body;
        if (body != null && !body.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(body);
                JsonNode errMsg = root.path("error").path("message");
                if (!errMsg.isMissingNode() && !errMsg.asText().isBlank()) {
                    detail = errMsg.asText();
                }
            } catch (JsonProcessingException ignored) {
                // fall back to raw body
            }
        }
        return e.getStatusCode() + " from POST " + url + (detail != null && !detail.isBlank() ? " — " + detail : "");
    }

    public Flux<LlmProvider.ChatChunk> chatStream(LlmProviderConfig config, LlmProvider.ChatRequest req) {
        ApiFlavor flavor = detectFlavor(config);
        if (flavor == ApiFlavor.RESPONSES) {
            return Flux.error(new UnsupportedOperationException(
                    "Streaming is not yet implemented for the OpenAI Responses API endpoint; " +
                            "use a /v1/chat/completions base URL or call chat() (non-streaming) instead."));
        }
        WebClient client = buildClient(config);
        String url = resolveUrl(config, flavor);
        String apiKey = decryptKey(config);

        OpenAiChatRequest body = toOpenAiRequest(req);
        body.model = config.getModel();
        body.stream = true;

        return client.post()
                .uri(url)
                .headers(h -> applyAuth(h, config, apiKey))
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(line -> Flux.fromIterable(parseSseLine(line)))
                .filter(chunk -> chunk != null);
    }

    /**
     * Upload a binary file via OpenAI's Files API. Use this for documents that
     * are too large to inline as base64 in the chat body (>5 MB or so) — the
     * chat request then references the upload by {@code file_id}, which lets
     * us push past the 25 MB request-body ceiling and unlocks file reuse
     * across calls.
     *
     * @return the {@code file_id} ("file-…") returned by OpenAI.
     */
    public String uploadFile(LlmProviderConfig config, byte[] bytes, String filename, String mimeType) {
        if (!isOpenAiBaseUrl(config.getBaseUrl())) {
            throw new UnsupportedOperationException(
                    "Files API is only supported when the configured provider is OpenAI (api.openai.com).");
        }
        WebClient client = buildClient(config);
        String apiKey = decryptKey(config);
        String filesUrl = resolveBaseRoot(config) + "/files";

        org.springframework.util.LinkedMultiValueMap<String, org.springframework.http.HttpEntity<?>> parts =
                new org.springframework.util.LinkedMultiValueMap<>();
        org.springframework.http.HttpHeaders fileHeaders = new org.springframework.http.HttpHeaders();
        fileHeaders.setContentType(mimeType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(mimeType));
        fileHeaders.setContentDispositionFormData("file", filename == null ? "upload.bin" : filename);
        parts.add("file", new org.springframework.http.HttpEntity<>(bytes, fileHeaders));
        parts.add("purpose", new org.springframework.http.HttpEntity<>("user_data"));

        try {
            String json = client.post()
                    .uri(filesUrl)
                    .headers(h -> {
                        h.setContentType(MediaType.MULTIPART_FORM_DATA);
                        h.setBearerAuth(apiKey);
                    })
                    .bodyValue(parts)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(Math.max(60_000L, config.getTimeoutMs())));
            JsonNode root = objectMapper.readTree(json);
            String fileId = root.path("id").asText();
            if (fileId == null || fileId.isBlank()) {
                throw new RuntimeException("Files API returned no id: " + json);
            }
            log.info("OpenAI Files: uploaded {} ({} bytes) -> {}", filename, bytes.length, fileId);
            return fileId;
        } catch (WebClientResponseException e) {
            throw new RuntimeException(formatHttpError(e, filesUrl), e);
        } catch (Exception e) {
            throw new RuntimeException("Files API upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a file previously uploaded via {@link #uploadFile}. Best-effort:
     * we never let a delete failure mask a successful generation.
     */
    public void deleteFile(LlmProviderConfig config, String fileId) {
        if (fileId == null || fileId.isBlank()) return;
        if (!isOpenAiBaseUrl(config.getBaseUrl())) return;
        WebClient client = buildClient(config);
        String apiKey = decryptKey(config);
        String url = resolveBaseRoot(config) + "/files/" + fileId;
        try {
            client.delete()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(apiKey))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(30_000L));
            log.info("OpenAI Files: deleted {}", fileId);
        } catch (Exception e) {
            log.warn("OpenAI Files: failed to delete {} ({}); leaking. Will be cleaned up by retention.",
                    fileId, e.getMessage());
        }
    }

    /** Trim trailing slash + any /chat/completions or /responses suffix → bare base ("…/v1"). */
    private String resolveBaseRoot(LlmProviderConfig config) {
        String base = config.getBaseUrl().trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String lower = base.toLowerCase();
        if (lower.endsWith("/chat/completions")) base = base.substring(0, base.length() - "/chat/completions".length());
        else if (lower.endsWith("/responses")) base = base.substring(0, base.length() - "/responses".length());
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    public LlmProvider.ChatResponse testConnection(LlmProviderConfig config) {
        // Use null temperature and a generous token budget so this works for both
        // chat models (which accept any temperature) and reasoning models like o1/o3/
        // gpt-5 (which only accept the default temperature and consume tokens on
        // hidden reasoning before producing visible output).
        LlmProvider.ChatRequest req = new LlmProvider.ChatRequest(
                List.of(new LlmProvider.Message("user", "reply with the word: pong")),
                null,
                256,
                null,
                (long) config.getTimeoutMs()
        );
        return chat(config, req);
    }

    /**
     * Floor on the effective request timeout for AI generation calls. A 30-page
     * native PDF read with reasoning legitimately takes 60–180 s; a misconfigured
     * 60 000 ms ceiling on the provider row should not silently abort it.
     * Operators can still set a higher value; this only protects against
     * pathologically tight ones.
     */
    private static final long MIN_GENERATION_TIMEOUT_MS = 300_000L;

    /** Resolve effective timeout: per-request override, else provider config, else 5 min floor. */
    private long effectiveTimeoutMs(LlmProviderConfig config, LlmProvider.ChatRequest req) {
        long fromRequest = req != null && req.timeoutMs() != null ? req.timeoutMs() : 0L;
        long fromConfig = config.getTimeoutMs();
        long candidate = Math.max(fromRequest, fromConfig);
        return Math.max(candidate, MIN_GENERATION_TIMEOUT_MS);
    }

    private WebClient buildClient(LlmProviderConfig config) {
        return buildClient(config.getTimeoutMs());
    }

    private WebClient buildClient(long timeoutMs) {
        // Force the JDK address resolver so DNS lookups go through the OS
        // (InetAddress.getAllByName → system resolver). Netty's default
        // DnsNameResolver needs the io.netty:netty-resolver-dns-native-macos
        // native lib on macOS; without it, it falls back to querying the
        // configured router directly, which times out on flaky AAAA / split
        // DNS networks. Using the JDK resolver makes us behave like every
        // other Java HTTP client on the box.
        HttpClient httpClient = HttpClient.create()
                .resolver(io.netty.resolver.DefaultAddressResolverGroup.INSTANCE)
                .responseTimeout(Duration.ofMillis(timeoutMs));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private enum ApiFlavor { CHAT, RESPONSES }

    private ApiFlavor detectFlavor(LlmProviderConfig config) {
        String base = config.getBaseUrl().trim().toLowerCase();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith("/responses") ? ApiFlavor.RESPONSES : ApiFlavor.CHAT;
    }

    /** Whether the configured base URL points at OpenAI's hosted API. */
    public static boolean isOpenAiBaseUrl(String baseUrl) {
        if (baseUrl == null) return false;
        String b = baseUrl.toLowerCase();
        return b.contains("api.openai.com");
    }

    private String resolveUrl(LlmProviderConfig config, ApiFlavor flavor) {
        String base = config.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        if ("AZURE".equalsIgnoreCase(config.getAuthScheme())) {
            return base + "/openai/deployments/" + config.getModel() + "/chat/completions?api-version=2024-08-01-preview";
        }

        String lower = base.toLowerCase();
        if (flavor == ApiFlavor.RESPONSES) {
            return lower.endsWith("/responses") ? base : base + "/responses";
        }
        return lower.endsWith("/chat/completions") ? base : base + "/chat/completions";
    }

    private String decryptKey(LlmProviderConfig config) {
        try {
            return apiKeyCipher.decrypt(config.getApiKeyIv(),
                                        config.getApiKeyCiphertext(),
                                        config.getApiKeyVersion());
        } catch (RuntimeException e) {
            // The cipher hides the underlying AEADBadTagException behind a generic
            // "Failed to decrypt API key". The most common cause is that
            // BIPROS_AI_KEK changed between the time the key was saved and now,
            // so the stored ciphertext is unrecoverable. Translate to a domain
            // exception so the UI can show the real next step.
            log.warn("Stored API key for provider {} cannot be decrypted: {}",
                    config.getName(), e.getMessage());
            throw new com.bipros.common.exception.BusinessRuleException(
                    "AI_PROVIDER_KEY_UNREADABLE",
                    "Stored API key for the active LLM provider cannot be decrypted. " +
                    "This usually means BIPROS_AI_KEK has changed since the key was saved. " +
                    "Re-enter the LLM provider's API key in Settings → LLM Providers.");
        }
    }

    private void applyAuth(HttpHeaders headers, LlmProviderConfig config, String apiKey) {
        String scheme = config.getAuthScheme();
        if ("API_KEY".equalsIgnoreCase(scheme) || "AZURE".equalsIgnoreCase(scheme)) {
            headers.set("api-key", apiKey);
        } else {
            headers.setBearerAuth(apiKey);
        }
        if (config.getExtraHeaders() != null && !config.getExtraHeaders().isBlank()) {
            try {
                JsonNode extra = objectMapper.readTree(config.getExtraHeaders());
                extra.fields().forEachRemaining(e -> headers.set(e.getKey(), e.getValue().asText()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse extra_headers JSON", e);
            }
        }
    }

    private OpenAiChatRequest toOpenAiRequest(LlmProvider.ChatRequest req) {
        OpenAiChatRequest r = new OpenAiChatRequest();
        r.model = null; // set per-provider if needed
        r.messages = req.messages().stream().map(m -> {
            if (m.imageUrl() != null && !m.imageUrl().isBlank()) {
                return new OpenAiMessage(m.role(), List.of(
                        new OpenAiContent("text", m.content(), null),
                        new OpenAiContent("image_url", null, new OpenAiImageUrl(m.imageUrl()))
                ), null, null);
            }
            // Tool result message: must reference the assistant's tool_call by id,
            // otherwise OpenAI rejects the request with
            // "messages with role 'tool' must be a response to a preceeding message
            //  with 'tool_calls'".
            if ("tool".equalsIgnoreCase(m.role()) && m.toolCallId() != null) {
                return new OpenAiMessage(m.role(), m.content(), m.toolCallId(), null);
            }
            // Assistant turn that issued tool calls: serialize the calls so the
            // following tool messages have something to reference.
            if ("assistant".equalsIgnoreCase(m.role()) && m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                List<OpenAiAssistantToolCall> calls = m.toolCalls().stream()
                        .map(tc -> new OpenAiAssistantToolCall(
                                tc.id(),
                                "function",
                                new OpenAiAssistantToolCallFunction(
                                        tc.name(),
                                        tc.arguments() == null ? "{}" : tc.arguments().toString())))
                        .toList();
                return new OpenAiMessage(m.role(), m.content(), null, calls);
            }
            return new OpenAiMessage(m.role(), m.content());
        }).toList();
        r.maxTokens = req.maxTokens();
        r.temperature = req.temperature();
        if (req.tools() != null && !req.tools().isEmpty()) {
            r.tools = req.tools().stream().map(t -> new OpenAiTool(t.name(), t.description(), t.parameters())).toList();
        }
        if (req.responseFormat() != null) {
            r.responseFormat = req.responseFormat();
        }
        return r;
    }

    LlmProvider.ChatResponse parseChatResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.path("choices");
            if (choices == null || choices.isMissingNode() || !choices.isArray() || choices.isEmpty()) {
                String errorDetail = root.path("error").path("message").asText("empty choices array");
                throw new RuntimeException("LLM returned no choices: " + errorDetail);
            }
            JsonNode choice = choices.get(0);
            if (choice == null || choice.isMissingNode()) {
                throw new RuntimeException("LLM returned null choice at index 0");
            }
            String finishReason = choice.path("finish_reason").asText("");
            JsonNode message = choice.path("message");
            String content = message.path("content").asText("");
            String refusal = message.path("refusal").asText("");
            String model = root.path("model").asText("");

            List<LlmProvider.ToolCall> toolCalls = new ArrayList<>();
            JsonNode tcNode = message.path("tool_calls");
            if (tcNode.isArray()) {
                for (JsonNode tc : tcNode) {
                    toolCalls.add(new LlmProvider.ToolCall(
                            tc.path("id").asText(),
                            tc.path("function").path("name").asText(),
                            parseToolArguments(tc.path("function").path("arguments"))
                    ));
                }
            }

            // A response with only a refusal block (OpenAI safety) carries no
            // useful content but a clear reason. Surface it instead of silently
            // returning empty text.
            if (!refusal.isBlank()) {
                log.warn("LLM refused to respond: finish_reason={}, refusal={}", finishReason, refusal);
                throw new RuntimeException("Model refused to respond: " + refusal);
            }

            // No content, no refusal, no tool calls => something went wrong but
            // the provider returned 200. Log the full body once so the operator
            // can see exactly why, then translate the finish_reason into an
            // actionable message.
            if (content.isBlank() && toolCalls.isEmpty()) {
                log.warn("LLM returned empty content. finish_reason={}, raw response: {}",
                        finishReason, json);
                throw new RuntimeException(emptyResponseMessage(finishReason));
            }

            JsonNode usage = root.path("usage");
            LlmProvider.Usage u = new LlmProvider.Usage(
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0),
                    usage.path("total_tokens").asInt(0)
            );

            return new LlmProvider.ChatResponse(content, toolCalls, u, model);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse LLM response", e);
        }
    }

    /**
     * Maps a Chat Completions {@code finish_reason} (or Responses API equivalent)
     * into a user-facing message that says what to do next.
     */
    static String emptyResponseMessage(String finishReason) {
        return switch (finishReason == null ? "" : finishReason) {
            case "length", "max_output_tokens" ->
                    "Model ran out of tokens before producing output. " +
                    "Increase Max Tokens for this provider in Settings " +
                    "(reasoning models typically need 16,000+).";
            case "content_filter" ->
                    "Provider safety filter blocked the response.";
            case "stop" ->
                    "Model returned an empty answer (finish_reason=stop). " +
                    "Likely a structured-output / response-format mismatch with this model.";
            case "" ->
                    "Model returned no content. See backend logs for the raw response payload.";
            default ->
                    "Model returned no content (finish_reason=" + finishReason + "). " +
                    "See backend logs for the raw response payload.";
        };
    }

    private JsonNode parseToolArguments(JsonNode argumentsNode) {
        if (argumentsNode == null || argumentsNode.isMissingNode() || argumentsNode.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (argumentsNode.isObject()) {
            return argumentsNode;
        }
        String raw = argumentsNode.asText();
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse tool arguments JSON: {}", raw, e);
            return objectMapper.createObjectNode();
        }
    }

    private List<LlmProvider.ChatChunk> parseSseLine(String line) {
        List<LlmProvider.ChatChunk> chunks = new ArrayList<>();
        if (line == null || line.isBlank()) {
            return chunks;
        }
        for (String part : line.split("\n")) {
            part = part.trim();
            if (!part.startsWith("data: ")) {
                continue;
            }
            String data = part.substring(6).trim();
            if ("[DONE]".equals(data)) {
                chunks.add(new LlmProvider.ChatChunk(null, null, "stop"));
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(data);
                JsonNode choices = root.path("choices");
                if (choices == null || choices.isMissingNode() || !choices.isArray() || choices.isEmpty()) {
                    continue;
                }
                JsonNode choice = choices.get(0);
                if (choice == null || choice.isMissingNode()) {
                    continue;
                }
                JsonNode delta = choice.path("delta");
                String text = delta.path("content").asText(null);
                String finishReason = choice.path("finish_reason").asText(null);
                JsonNode tcNode = delta.path("tool_calls");
                boolean emittedToolCall = false;
                if (tcNode.isArray() && tcNode.size() > 0) {
                    // Emit one chunk per tool_call entry. Subsequent argument
                    // deltas usually carry only `index`+`function.arguments` —
                    // we propagate `index` so the orchestrator can accumulate
                    // by call slot rather than relying on `id`, which only
                    // appears on the first delta of each call.
                    for (JsonNode tc : tcNode) {
                        int idx = tc.path("index").asInt(0);
                        String id = tc.hasNonNull("id") ? tc.get("id").asText() : null;
                        JsonNode fn = tc.path("function");
                        String name = fn.hasNonNull("name") ? fn.get("name").asText() : null;
                        String argsDelta = fn.hasNonNull("arguments") ? fn.get("arguments").asText() : null;
                        LlmProvider.ToolCallDelta tcd = new LlmProvider.ToolCallDelta(idx, id, name, argsDelta);
                        chunks.add(new LlmProvider.ChatChunk(null, tcd, null));
                        emittedToolCall = true;
                    }
                }
                if (!emittedToolCall || text != null || finishReason != null) {
                    chunks.add(new LlmProvider.ChatChunk(text, null, finishReason));
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse SSE data line: {}", data);
            }
        }
        return chunks;
    }

    private OpenAiResponsesRequest toResponsesRequest(LlmProvider.ChatRequest req) {
        OpenAiResponsesRequest r = new OpenAiResponsesRequest();
        r.input = buildResponsesInput(req);
        r.maxOutputTokens = req.maxTokens();
        // Deliberately do not pass `temperature` for the Responses API: OpenAI's reasoning
        // models (the primary callers of this endpoint) only accept the default value, and
        // the Responses API exposes `reasoning.effort` / `text.verbosity` for control instead.
        if (req.tools() != null && !req.tools().isEmpty()) {
            r.tools = req.tools().stream()
                    .map(t -> new OpenAiResponsesTool("function", t.name(), t.description(), t.parameters()))
                    .toList();
        }
        if (req.responseFormat() != null) {
            r.text = new OpenAiResponsesText(toResponsesFormat(req.responseFormat()), null);
        }
        if (req.reasoningEffort() != null && !req.reasoningEffort().isBlank()) {
            // Raw value; resolved against the model's accepted vocabulary in chat()
            // where we have both the config and the request together.
            r.reasoning = new OpenAiResponsesReasoning(req.reasoningEffort());
        }
        return r;
    }

    /**
     * Build the {@code input} array. The last user message receives any attached
     * documents as {@code input_file} blocks (model reads PDFs natively). All
     * other messages stay text-only.
     */
    private List<OpenAiResponsesMessage> buildResponsesInput(LlmProvider.ChatRequest req) {
        List<LlmProvider.Message> messages = req.messages();
        List<LlmProvider.DocumentInput> docs = req.documents();
        if (docs == null || docs.isEmpty()) {
            return messages.stream()
                    .map(m -> new OpenAiResponsesMessage(m.role(),
                            List.of(new OpenAiResponsesContentBlock(
                                    "system".equalsIgnoreCase(m.role()) ? "input_text" : "input_text",
                                    m.content(), null, null))))
                    .toList();
        }

        // Find the index of the last user message; documents attach there.
        int lastUserIdx = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equalsIgnoreCase(messages.get(i).role())) {
                lastUserIdx = i;
                break;
            }
        }
        List<OpenAiResponsesMessage> out = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            LlmProvider.Message m = messages.get(i);
            List<OpenAiResponsesContentBlock> content = new ArrayList<>();
            if (i == lastUserIdx) {
                // Files come first so the model has them in working memory before reading the prompt.
                for (LlmProvider.DocumentInput d : docs) {
                    if (d.fileId() != null && !d.fileId().isBlank()) {
                        // By-reference: the file was previously uploaded via Files API.
                        content.add(new OpenAiResponsesContentBlock("input_file", null, null, null, d.fileId()));
                    } else {
                        // Inline: base64-encode the bytes into a data URI.
                        String dataUri = "data:" + (d.mimeType() == null ? "application/pdf" : d.mimeType())
                                + ";base64,"
                                + java.util.Base64.getEncoder().encodeToString(d.data());
                        content.add(new OpenAiResponsesContentBlock("input_file", null, d.filename(), dataUri, null));
                    }
                }
            }
            content.add(new OpenAiResponsesContentBlock("input_text", m.content(), null, null));
            out.add(new OpenAiResponsesMessage(m.role(), content));
        }
        return out;
    }

    /**
     * Translate the Chat-Completions response_format wrapper
     * {@code {type:"json_schema", json_schema:{name, strict, schema}}} into the
     * Responses API's flat {@code text.format} shape
     * {@code {type:"json_schema", name, strict, schema}}.
     * If the input is already in Responses shape, it is returned unchanged.
     */
    JsonNode toResponsesFormat(JsonNode responseFormat) {
        if (responseFormat == null || !responseFormat.has("json_schema")) {
            return responseFormat;
        }
        com.fasterxml.jackson.databind.node.ObjectNode flat = objectMapper.createObjectNode();
        flat.set("type", responseFormat.path("type").isMissingNode()
                ? com.fasterxml.jackson.databind.node.TextNode.valueOf("json_schema")
                : responseFormat.path("type"));
        JsonNode js = responseFormat.path("json_schema");
        if (!js.path("name").isMissingNode())   flat.set("name",   js.get("name"));
        if (!js.path("strict").isMissingNode()) flat.set("strict", js.get("strict"));
        if (!js.path("schema").isMissingNode()) flat.set("schema", js.get("schema"));
        return flat;
    }

    private LlmProvider.ChatResponse parseResponsesResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String model = root.path("model").asText("");
            String status = root.path("status").asText("");
            String incompleteReason = root.path("incomplete_details").path("reason").asText("");

            StringBuilder content = new StringBuilder();
            String refusal = "";
            List<LlmProvider.ToolCall> toolCalls = new ArrayList<>();
            JsonNode output = root.path("output");
            if (output.isArray()) {
                for (JsonNode item : output) {
                    String type = item.path("type").asText("");
                    if ("message".equals(type)) {
                        JsonNode msgContent = item.path("content");
                        if (msgContent.isArray()) {
                            for (JsonNode c : msgContent) {
                                String cType = c.path("type").asText("");
                                if ("output_text".equals(cType) || "text".equals(cType)) {
                                    content.append(c.path("text").asText(""));
                                } else if ("refusal".equals(cType)) {
                                    String r = c.path("refusal").asText("");
                                    if (!r.isBlank()) refusal = r;
                                }
                            }
                        }
                    } else if ("function_call".equals(type)) {
                        toolCalls.add(new LlmProvider.ToolCall(
                                item.path("call_id").asText(item.path("id").asText("")),
                                item.path("name").asText(""),
                                parseToolArguments(item.path("arguments"))
                        ));
                    }
                }
            }
            // SDK convenience field — present on simple text-only responses
            if (content.length() == 0) {
                JsonNode outputText = root.path("output_text");
                if (!outputText.isMissingNode() && !outputText.isNull()) {
                    content.append(outputText.asText(""));
                }
            }

            if (!refusal.isBlank()) {
                log.warn("LLM (Responses API) refused: status={}, refusal={}", status, refusal);
                throw new RuntimeException("Model refused to respond: " + refusal);
            }
            // status="incomplete" covers both the empty-output case AND the more
            // common "model produced partial JSON before hitting max_output_tokens"
            // case. Detect this BEFORE attempting to parse the JSON downstream —
            // partial JSON will throw a JsonEOFException whose message is much
            // less actionable than "Increase Max Tokens".
            boolean incomplete = "incomplete".equalsIgnoreCase(status)
                    || (!incompleteReason.isBlank() && content.length() > 0);
            if (incomplete) {
                String reason = !incompleteReason.isBlank() ? incompleteReason
                        : (!status.isBlank() ? status : "");
                log.warn("LLM (Responses API) returned incomplete output. status={}, incomplete_details.reason={}, " +
                                "partial_chars={}, raw response begin: {}",
                        status, incompleteReason, content.length(),
                        json.substring(0, Math.min(json.length(), 800)));
                throw new RuntimeException(emptyResponseMessage(reason));
            }
            if (content.length() == 0 && toolCalls.isEmpty()) {
                String reason = !incompleteReason.isBlank() ? incompleteReason
                        : (!status.isBlank() ? status : "");
                log.warn("LLM (Responses API) returned empty output. status={}, incomplete_details.reason={}, raw response: {}",
                        status, incompleteReason, json);
                throw new RuntimeException(emptyResponseMessage(reason));
            }

            JsonNode usage = root.path("usage");
            int inTok = usage.path("input_tokens").asInt(0);
            int outTok = usage.path("output_tokens").asInt(0);
            int totalTok = usage.path("total_tokens").asInt(inTok + outTok);
            LlmProvider.Usage u = new LlmProvider.Usage(inTok, outTok, totalTok);

            return new LlmProvider.ChatResponse(content.toString(), toolCalls, u, model);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse LLM Responses API response", e);
        }
    }

    // OpenAI request/response DTOs
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private static class OpenAiChatRequest {
        public String model;
        public List<OpenAiMessage> messages;
        public List<OpenAiTool> tools;
        @com.fasterxml.jackson.annotation.JsonProperty("max_completion_tokens")
        public Integer maxTokens;
        public Double temperature;
        public boolean stream = false;
        @com.fasterxml.jackson.annotation.JsonProperty("response_format")
        public Object responseFormat;
    }

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private record OpenAiMessage(
            String role,
            Object content,
            @com.fasterxml.jackson.annotation.JsonProperty("tool_call_id") String toolCallId,
            @com.fasterxml.jackson.annotation.JsonProperty("tool_calls") List<OpenAiAssistantToolCall> toolCalls) {
        OpenAiMessage(String role, Object content) {
            this(role, content, null, null);
        }
    }

    /** Entry inside an assistant message's {@code tool_calls} array. */
    private record OpenAiAssistantToolCall(String id, String type, OpenAiAssistantToolCallFunction function) {
    }

    /** Function detail inside a tool_calls entry. {@code arguments} is a JSON-encoded string. */
    private record OpenAiAssistantToolCallFunction(String name, String arguments) {
    }

    private record OpenAiContent(
            String type,
            String text,
            @com.fasterxml.jackson.annotation.JsonProperty("image_url") OpenAiImageUrl imageUrl) {
    }

    private record OpenAiImageUrl(String url) {
    }

    private record OpenAiTool(String type, OpenAiToolFunction function) {
        OpenAiTool(String name, String description, JsonNode parameters) {
            this("function", new OpenAiToolFunction(name, description, parameters));
        }
    }

    private record OpenAiToolFunction(String name, String description, JsonNode parameters) {
    }

    // Responses API DTOs (POST /v1/responses) — distinct wire format from Chat Completions.
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private static class OpenAiResponsesRequest {
        public String model;
        public List<OpenAiResponsesMessage> input;
        public List<OpenAiResponsesTool> tools;
        @com.fasterxml.jackson.annotation.JsonProperty("max_output_tokens")
        public Integer maxOutputTokens;
        public Double temperature;
        public OpenAiResponsesText text;
        public OpenAiResponsesReasoning reasoning;
        public boolean stream = false;
    }

    /** Message content is a list of typed blocks: input_text, input_file, input_image. */
    private record OpenAiResponsesMessage(String role, List<OpenAiResponsesContentBlock> content) {
    }

    /**
     * One content block. Shapes:
     *   {type:"input_text", text:"..."}
     *   {type:"input_file", filename:"...", file_data:"data:application/pdf;base64,..."}   (inline ≤ ~5 MB)
     *   {type:"input_file", file_id:"file-..."}                                            (Files API > 5 MB)
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private record OpenAiResponsesContentBlock(
            String type,
            String text,
            String filename,
            @com.fasterxml.jackson.annotation.JsonProperty("file_data") String fileData,
            @com.fasterxml.jackson.annotation.JsonProperty("file_id") String fileId) {
        OpenAiResponsesContentBlock(String type, String text, String filename, String fileData) {
            this(type, text, filename, fileData, null);
        }
    }

    private record OpenAiResponsesTool(String type, String name, String description, JsonNode parameters) {
    }

    private record OpenAiResponsesText(
            @com.fasterxml.jackson.annotation.JsonProperty("format") Object format,
            @com.fasterxml.jackson.annotation.JsonProperty("verbosity") String verbosity) {
    }

    /** Controls reasoning depth on reasoning-capable models. effort ∈ {minimal, low, medium, high}. */
    private record OpenAiResponsesReasoning(String effort) {
    }
}
