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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmProvider.ChatResponse chat(LlmProviderConfig config, LlmProvider.ChatRequest req) {
        ApiFlavor flavor = detectFlavor(config);
        WebClient client = buildClient(config);
        String url = resolveUrl(config, flavor);
        String apiKey = decryptKey(config);

        Object body;
        if (flavor == ApiFlavor.RESPONSES) {
            OpenAiResponsesRequest r = toResponsesRequest(req);
            r.model = config.getModel();
            body = r;
        } else {
            OpenAiChatRequest r = toOpenAiRequest(req);
            r.model = config.getModel();
            body = r;
        }

        String responseJson;
        try {
            responseJson = client.post()
                    .uri(url)
                    .headers(h -> applyAuth(h, config, apiKey))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(config.getTimeoutMs()));
        } catch (WebClientResponseException e) {
            throw new RuntimeException(formatHttpError(e, url), e);
        }

        return flavor == ApiFlavor.RESPONSES
                ? parseResponsesResponse(responseJson)
                : parseChatResponse(responseJson);
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

    private WebClient buildClient(LlmProviderConfig config) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(config.getTimeoutMs()));
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
        return apiKeyCipher.decrypt(config.getApiKeyIv(), config.getApiKeyCiphertext(), config.getApiKeyVersion());
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
                ));
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

    private LlmProvider.ChatResponse parseChatResponse(String json) {
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
            JsonNode message = choice.path("message");
            String content = message.path("content").asText("");
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
                LlmProvider.ToolCallDelta tcd = null;
                JsonNode tcNode = delta.path("tool_calls");
                if (tcNode.isArray() && tcNode.size() > 0) {
                    JsonNode tc = tcNode.get(0);
                    tcd = new LlmProvider.ToolCallDelta(
                            tc.path("id").asText(null),
                            tc.path("function").path("name").asText(null),
                            tc.path("function").path("arguments").asText(null)
                    );
                }
                chunks.add(new LlmProvider.ChatChunk(text, tcd, finishReason));
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse SSE data line: {}", data);
            }
        }
        return chunks;
    }

    private OpenAiResponsesRequest toResponsesRequest(LlmProvider.ChatRequest req) {
        OpenAiResponsesRequest r = new OpenAiResponsesRequest();
        r.input = req.messages().stream()
                .map(m -> new OpenAiResponsesMessage(m.role(), m.content()))
                .toList();
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
            r.text = new OpenAiResponsesText(req.responseFormat(), null);
        }
        return r;
    }

    private LlmProvider.ChatResponse parseResponsesResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String model = root.path("model").asText("");

            StringBuilder content = new StringBuilder();
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

    private record OpenAiMessage(String role, Object content) {
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
        public boolean stream = false;
    }

    private record OpenAiResponsesMessage(String role, String content) {
    }

    private record OpenAiResponsesTool(String type, String name, String description, JsonNode parameters) {
    }

    private record OpenAiResponsesText(
            @com.fasterxml.jackson.annotation.JsonProperty("format") Object format,
            @com.fasterxml.jackson.annotation.JsonProperty("verbosity") String verbosity) {
    }
}
