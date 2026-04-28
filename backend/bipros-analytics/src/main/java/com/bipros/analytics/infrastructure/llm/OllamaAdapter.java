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
public class OllamaAdapter implements LlmAdapter {

    private static final String DEFAULT_ENDPOINT = "http://localhost:11434";
    private final ObjectMapper json;

    @Override public LlmProvider provider() { return LlmProvider.OLLAMA; }

    @Override
    public CompletionResponse complete(CompletionRequest req, String apiKey, String endpoint, String model) {
        String base = endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint;
        RestClient client = RestClient.builder().baseUrl(base).build();

        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        ArrayNode msgs = body.putArray("messages");
        if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) {
            ObjectNode sys = msgs.addObject();
            sys.put("role", "system");
            sys.put("content", req.systemPrompt());
        }
        for (Message m : req.conversation()) {
            ObjectNode mn = msgs.addObject();
            mn.put("role", switch (m.role()) {
                case USER -> "user";
                case ASSISTANT -> "assistant";
                case SYSTEM -> "system";
            });
            mn.put("content", m.content());
        }
        ObjectNode opts = body.putObject("options");
        opts.put("num_predict", req.maxTokens());

        JsonNode resp = client.post()
            .uri("/api/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode.class);

        String text = resp != null ? resp.path("message").path("content").asText("") : "";
        int in = resp != null ? resp.path("prompt_eval_count").asInt(0) : 0;
        int out = resp != null ? resp.path("eval_count").asInt(0) : 0;
        return new CompletionResponse(text, new TokenUsage(in, out));
    }
}
