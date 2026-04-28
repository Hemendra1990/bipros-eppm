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
public class GoogleGeminiAdapter implements LlmAdapter {

    private static final String DEFAULT_ENDPOINT = "https://generativelanguage.googleapis.com";
    private final ObjectMapper json;

    @Override public LlmProvider provider() { return LlmProvider.GOOGLE; }

    @Override
    public CompletionResponse complete(CompletionRequest req, String apiKey, String endpoint, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Gemini API key is required");
        }
        String base = endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint;
        RestClient client = RestClient.builder().baseUrl(base).build();

        ObjectNode body = json.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        for (Message m : req.conversation()) {
            ObjectNode part = contents.addObject();
            part.put("role", m.role() == Message.Role.ASSISTANT ? "model" : "user");
            ArrayNode parts = part.putArray("parts");
            parts.addObject().put("text", m.content());
        }
        if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) {
            ObjectNode sys = body.putObject("systemInstruction");
            ArrayNode parts = sys.putArray("parts");
            parts.addObject().put("text", req.systemPrompt());
        }
        ObjectNode genCfg = body.putObject("generationConfig");
        genCfg.put("maxOutputTokens", req.maxTokens());

        JsonNode resp = client.post()
            .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode.class);

        String text = resp != null
            ? resp.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("")
            : "";
        int in = resp != null ? resp.path("usageMetadata").path("promptTokenCount").asInt(0) : 0;
        int out = resp != null ? resp.path("usageMetadata").path("candidatesTokenCount").asInt(0) : 0;
        return new CompletionResponse(text, new TokenUsage(in, out));
    }
}
