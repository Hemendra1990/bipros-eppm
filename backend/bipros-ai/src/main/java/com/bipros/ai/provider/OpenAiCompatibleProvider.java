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
        WebClient client = buildClient(config);
        String url = resolveUrl(config);
        String apiKey = decryptKey(config);

        OpenAiChatRequest body = toOpenAiRequest(req);
        body.model = config.getModel();

        String responseJson = client.post()
                .uri(url)
                .headers(h -> applyAuth(h, config, apiKey))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(config.getTimeoutMs()));

        return parseChatResponse(responseJson);
    }

    public Flux<LlmProvider.ChatChunk> chatStream(LlmProviderConfig config, LlmProvider.ChatRequest req) {
        WebClient client = buildClient(config);
        String url = resolveUrl(config);
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
        LlmProvider.ChatRequest req = new LlmProvider.ChatRequest(
                List.of(new LlmProvider.Message("user", "reply with the word: pong")),
                null,
                10,
                0.0,
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

    private String resolveUrl(LlmProviderConfig config) {
        String base = config.getBaseUrl();
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        if ("AZURE".equalsIgnoreCase(config.getAuthScheme())) {
            return base + "openai/deployments/" + config.getModel() + "/chat/completions?api-version=2024-08-01-preview";
        }
        return base + "chat/completions";
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
                return new OpenAiMessage(m.role(), null, List.of(
                        new OpenAiContent("text", m.content(), null),
                        new OpenAiContent("image_url", null, new OpenAiImageUrl(m.imageUrl()))
                ));
            }
            return new OpenAiMessage(m.role(), m.content(), null);
        }).toList();
        r.maxTokens = req.maxTokens();
        r.temperature = req.temperature();
        if (req.tools() != null && !req.tools().isEmpty()) {
            r.tools = req.tools().stream().map(t -> new OpenAiTool(t.name(), t.description(), t.parameters())).toList();
        }
        return r;
    }

    private LlmProvider.ChatResponse parseChatResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode choice = root.path("choices").get(0);
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
                JsonNode delta = root.path("choices").get(0).path("delta");
                String text = delta.path("content").asText(null);
                String finishReason = root.path("choices").get(0).path("finish_reason").asText(null);
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

    // OpenAI request/response DTOs
    private static class OpenAiChatRequest {
        public String model;
        public List<OpenAiMessage> messages;
        public List<OpenAiTool> tools;
        public Integer maxTokens;
        public Double temperature;
        public boolean stream = false;
    }

    private record OpenAiMessage(String role, String content, List<OpenAiContent> contentArray) {
    }

    private record OpenAiContent(String type, String text, OpenAiImageUrl imageUrl) {
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
}
