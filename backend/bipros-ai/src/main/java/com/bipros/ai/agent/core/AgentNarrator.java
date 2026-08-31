package com.bipros.ai.agent.core;

import com.bipros.ai.provider.LlmProvider;
import com.bipros.ai.provider.LlmProviderConfig;
import com.bipros.ai.provider.LlmProviderConfigRepository;
import com.bipros.ai.provider.OpenAiCompatibleProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The single schema-strict LLM call in an agent run. Given the deterministic candidate findings,
 * it returns polished, executive-ready prose — <b>and nothing else</b>. The response schema is keyed
 * by candidate index and carries only the five narrative fields, so the model physically cannot add
 * a finding, drop a deterministic one, or change a number/severity/confidence. A candidate whose
 * index the model omits simply keeps its templated text.
 *
 * <p>Cloned from {@code InsightsGenerator}: resolve default provider → build strict json_schema
 * {@code responseFormat} → {@code OpenAiCompatibleProvider.chat}. One parse-retry; on failure the
 * caller ({@code AbstractAgent}) falls back to the templated candidates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentNarrator {

    private final LlmProviderConfigRepository llmProviderConfigRepository;
    private final OpenAiCompatibleProvider openAiCompatibleProvider;
    private final ObjectMapper objectMapper;

    /** Result of a narration call: rewritten drafts plus token accounting. */
    public record NarrationResult(List<AgentFindingDraft> drafts, int tokensInput, int tokensOutput, String model) {
    }

    /** Thrown when the LLM call or its parse fails after retry — caller uses the templated fallback. */
    public static class NarrationException extends RuntimeException {
        public NarrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public Optional<LlmProviderConfig> defaultConfig() {
        return llmProviderConfigRepository.findByIsDefaultTrueAndIsActiveTrue()
                .or(llmProviderConfigRepository::findFirstByIsActiveTrueOrderByIsDefaultDescCreatedAtAsc);
    }

    /**
     * Narrate the candidates. Never mutates numbers; returns candidates with rewritten prose merged
     * by index. Throws {@link NarrationException} on any LLM/parse failure after one retry.
     */
    public NarrationResult narrate(String agentDisplayName, JsonNode dataSnapshot,
                                   List<AgentFindingDraft> candidates, LlmProviderConfig config) {
        String prompt = buildPrompt(agentDisplayName, dataSnapshot, candidates);
        JsonNode responseFormat = buildResponseFormat();

        List<LlmProvider.Message> messages = List.of(
                new LlmProvider.Message("system",
                        "You are a senior construction project-controls analyst writing findings for a project "
                        + "intelligence dashboard. You are given machine-computed findings. Rewrite ONLY the prose "
                        + "(title, whatHappened, whyItHappened, businessImpact, recommendedAction) to be precise, "
                        + "concrete and executive-ready. You MUST NOT change, add or remove any number, percentage, "
                        + "date, severity or confidence, and you MUST NOT invent findings. Return one narration per "
                        + "candidate index, as JSON matching the schema."),
                new LlmProvider.Message("user", prompt));

        LlmProvider.ChatRequest req = new LlmProvider.ChatRequest(
                messages, null, config.getMaxTokens(), config.getTemperature().doubleValue(),
                (long) config.getTimeoutMs(), responseFormat);

        LlmProvider.ChatResponse resp = callWithRetry(config, req);
        List<AgentFindingDraft> narrated = mergeNarrations(candidates, resp.content());

        int tin = resp.usage() != null ? resp.usage().promptTokens() : 0;
        int tout = resp.usage() != null ? resp.usage().completionTokens() : 0;
        return new NarrationResult(narrated, tin, tout, resp.model());
    }

    private LlmProvider.ChatResponse callWithRetry(LlmProviderConfig config, LlmProvider.ChatRequest req) {
        try {
            LlmProvider.ChatResponse r = openAiCompatibleProvider.chat(config, req);
            if (r.content() == null || r.content().isBlank()) {
                throw new IllegalStateException("empty content");
            }
            return r;
        } catch (Exception first) {
            log.warn("Narration LLM call failed once ({}); retrying", first.getMessage());
            try {
                LlmProvider.ChatResponse r = openAiCompatibleProvider.chat(config, req);
                if (r.content() == null || r.content().isBlank()) {
                    throw new IllegalStateException("empty content on retry");
                }
                return r;
            } catch (Exception second) {
                throw new NarrationException("Narration failed after retry: " + second.getMessage(), second);
            }
        }
    }

    private List<AgentFindingDraft> mergeNarrations(List<AgentFindingDraft> candidates, String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode arr = root.path("narrations");
            List<AgentFindingDraft> out = new ArrayList<>(candidates);
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    int idx = n.path("index").asInt(-1);
                    if (idx < 0 || idx >= out.size()) continue;
                    out.set(idx, out.get(idx).withNarrative(
                            text(n, "title"), text(n, "whatHappened"), text(n, "whyItHappened"),
                            text(n, "businessImpact"), text(n, "recommendedAction")));
                }
            }
            return out;
        } catch (Exception e) {
            throw new NarrationException("Failed to parse narration JSON: " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private String buildPrompt(String agentDisplayName, JsonNode dataSnapshot, List<AgentFindingDraft> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("Agent: ").append(agentDisplayName).append('\n');
        sb.append("Underlying data snapshot (do not restate raw JSON in prose):\n");
        sb.append(dataSnapshot == null ? "{}" : dataSnapshot.toString()).append("\n\n");
        sb.append("Candidate findings to narrate (index → deterministic fields):\n");
        for (int i = 0; i < candidates.size(); i++) {
            AgentFindingDraft d = candidates.get(i);
            sb.append("[").append(i).append("] type=").append(d.findingType())
                    .append(" severity=").append(d.severity())
                    .append(" confidence=").append(String.format(java.util.Locale.ROOT, "%.2f", d.confidence()))
                    .append(" (").append(d.confidenceBasis()).append(")\n");
            sb.append("    title: ").append(d.title()).append('\n');
            sb.append("    whatHappened: ").append(d.whatHappened()).append('\n');
            sb.append("    whyItHappened: ").append(d.whyItHappened()).append('\n');
            sb.append("    businessImpact: ").append(d.businessImpact()).append('\n');
            sb.append("    recommendedAction: ").append(d.recommendedAction()).append('\n');
            if (!d.evidence().isEmpty()) {
                sb.append("    evidence: ");
                d.evidence().forEach(e -> sb.append(e.label()).append('=').append(e.value()).append("; "));
                sb.append('\n');
            }
        }
        sb.append("\nReturn a 'narrations' array with one object per index you improved. ")
                .append("Keep every number identical to the candidate.");
        return sb.toString();
    }

    /** Strict json_schema response format (OpenAI shape), matching InsightsSchemaBuilder's wrapper. */
    private JsonNode buildResponseFormat() {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("type", "object");
        ObjectNode props = item.putObject("properties");
        props.putObject("index").put("type", "integer");
        for (String f : List.of("title", "whatHappened", "whyItHappened", "businessImpact", "recommendedAction")) {
            props.putObject(f).put("type", "string");
        }
        ArrayNode required = item.putArray("required");
        required.add("index").add("title").add("whatHappened").add("whyItHappened")
                .add("businessImpact").add("recommendedAction");
        item.put("additionalProperties", false);

        ObjectNode narrations = objectMapper.createObjectNode();
        narrations.put("type", "array");
        narrations.set("items", item);

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties").set("narrations", narrations);
        schema.putArray("required").add("narrations");
        schema.put("additionalProperties", false);

        ObjectNode jsonSchema = objectMapper.createObjectNode();
        jsonSchema.put("name", "agent_narration");
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", schema);

        ObjectNode responseFormat = objectMapper.createObjectNode();
        responseFormat.put("type", "json_schema");
        responseFormat.set("json_schema", jsonSchema);
        return responseFormat;
    }
}
