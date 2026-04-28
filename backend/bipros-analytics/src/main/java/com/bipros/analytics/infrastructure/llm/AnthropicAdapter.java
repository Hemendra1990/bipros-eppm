package com.bipros.analytics.infrastructure.llm;

import com.bipros.analytics.domain.model.LlmProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "bipros.analytics.assistant.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AnthropicAdapter implements LlmAdapter {

    private static final String DEFAULT_ENDPOINT = "https://api.anthropic.com";
    private final ObjectMapper json;

    @Override public LlmProvider provider() { return LlmProvider.ANTHROPIC; }

    @Override
    public CompletionResponse complete(CompletionRequest req, String apiKey, String endpoint, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Anthropic API key is required");
        }
        String base = endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint;
        RestClient client = RestClient.builder()
            .baseUrl(base)
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader("content-type", "application/json")
            .build();

        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", req.maxTokens());
        if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) {
            body.put("system", req.systemPrompt());
        }
        ArrayNode msgs = body.putArray("messages");
        for (Message m : req.conversation()) {
            if (m.role() == Message.Role.SYSTEM) continue;
            ObjectNode mn = msgs.addObject();
            mn.put("role", m.role() == Message.Role.USER ? "user" : "assistant");
            mn.put("content", m.content());
        }

        JsonNode resp = client.post()
            .uri("/v1/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode.class);

        String text = "";
        if (resp != null && resp.has("content") && resp.get("content").isArray() && resp.get("content").size() > 0) {
            JsonNode first = resp.get("content").get(0);
            if (first.has("text")) text = first.get("text").asText();
        }
        int in = resp != null && resp.has("usage") ? resp.get("usage").path("input_tokens").asInt(0) : 0;
        int out = resp != null && resp.has("usage") ? resp.get("usage").path("output_tokens").asInt(0) : 0;
        return new CompletionResponse(text, new TokenUsage(in, out));
    }
}
