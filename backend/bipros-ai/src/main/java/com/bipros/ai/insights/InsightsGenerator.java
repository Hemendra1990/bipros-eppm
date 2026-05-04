package com.bipros.ai.insights;

import com.bipros.ai.insights.dto.ChartSpec;
import com.bipros.ai.insights.dto.InsightsResponse;
import com.bipros.ai.provider.LlmProvider;
import com.bipros.ai.provider.LlmProviderConfig;
import com.bipros.ai.provider.LlmProviderConfigRepository;
import com.bipros.ai.provider.OpenAiCompatibleProvider;
import com.bipros.common.exception.BusinessRuleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsightsGenerator {

    private final LlmProviderConfigRepository llmProviderConfigRepository;
    private final OpenAiCompatibleProvider openAiCompatibleProvider;
    private final InsightsSchemaBuilder insightsSchemaBuilder;
    private final ObjectMapper objectMapper;

    public InsightsResponse generate(String tabKey, JsonNode dataSnapshot, String promptInstructions,
                                     List<ChartSpec> charts) {
        log.info("Generating insights for tab: {}", tabKey);

        LlmProviderConfig config = llmProviderConfigRepository.findByIsDefaultTrueAndIsActiveTrue()
                .or(() -> llmProviderConfigRepository.findFirstByIsActiveTrueOrderByIsDefaultDescCreatedAtAsc())
                .orElseThrow(() -> new BusinessRuleException("AI_NOT_CONFIGURED",
                        "No active AI provider configured. Please configure an LLM provider in Settings."));

        String prompt = buildPrompt(tabKey, dataSnapshot, promptInstructions);
        JsonNode responseSchema = insightsSchemaBuilder.buildSchema();

        List<LlmProvider.Message> messages = List.of(
                new LlmProvider.Message("system",
                        "You are a construction project management AI analyst. " +
                        "Analyze the provided project data and return ONLY valid JSON matching the provided schema. " +
                        "No markdown, no explanations outside the JSON."),
                new LlmProvider.Message("user", prompt)
        );

        LlmProvider.ChatRequest chatReq = new LlmProvider.ChatRequest(
                messages, null, config.getMaxTokens(),
                config.getTemperature().doubleValue(), (long) config.getTimeoutMs(),
                responseSchema
        );

        LlmProvider.ChatResponse resp;
        try {
            resp = openAiCompatibleProvider.chat(config, chatReq);
        } catch (Exception e) {
            log.error("LLM call failed for insights generation", e);
            throw new BusinessRuleException("AI_INSIGHT_GENERATION_FAILED", "AI insight generation failed: " + e.getMessage());
        }

        if (resp.content() == null || resp.content().isBlank()) {
            throw new BusinessRuleException("AI_INSIGHT_GENERATION_FAILED", "AI returned empty response");
        }

        InsightsResponse parsed;
        try {
            JsonNode root = objectMapper.readTree(resp.content());
            parsed = objectMapper.convertValue(root, InsightsResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse AI insights response: {}", resp.content(), e);
            throw new BusinessRuleException("AI_INSIGHT_GENERATION_FAILED", "Failed to parse AI response: " + e.getMessage());
        }

        if (isEmpty(parsed)) {
            log.warn("AI insights response was empty/all-null for tab={}, raw content={}", tabKey, resp.content());
            throw new BusinessRuleException("AI_INSIGHT_GENERATION_FAILED",
                    "AI returned an empty insights payload — the configured model may not support strict JSON schema responses, or the input data was too sparse.");
        }

        return withCharts(parsed, charts);
    }

    /**
     * Merge server-built charts into an InsightsResponse. Charts are deterministic and
     * always sourced from the collector — never from the LLM. Used both right after the
     * LLM call and on cache hits, so cached responses always render with fresh charts.
     */
    public static InsightsResponse withCharts(InsightsResponse response, List<ChartSpec> charts) {
        if (response == null) return null;
        List<ChartSpec> merged = (charts != null && !charts.isEmpty()) ? charts : response.charts();
        return new InsightsResponse(
                response.summary(), response.highlights(), response.variances(),
                response.recommendations(), response.findings(), response.rationale(),
                response.mdx(), merged
        );
    }

    private static boolean isEmpty(InsightsResponse r) {
        if (r == null) return true;
        boolean blankSummary = r.summary() == null || r.summary().isBlank();
        boolean blankRationale = r.rationale() == null || r.rationale().isBlank();
        boolean noHighlights = r.highlights() == null || r.highlights().isEmpty();
        boolean noVariances = r.variances() == null || r.variances().isEmpty();
        boolean noRecommendations = r.recommendations() == null || r.recommendations().isEmpty();
        boolean noFindings = r.findings() == null || r.findings().isEmpty();
        return blankSummary && blankRationale && noHighlights && noVariances && noRecommendations && noFindings;
    }

    private String buildPrompt(String tabKey, JsonNode dataSnapshot, String promptInstructions) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are reviewing the ").append(tabKey).append(" tab for a construction/infrastructure project.\n");
        sb.append("The data below is what the user is currently looking at on screen.\n\n");
        sb.append(dataSnapshot.toString()).append("\n\n");
        sb.append(promptInstructions).append("\n\n");
        sb.append("Produce structured insights matching the provided schema exactly.");
        return sb.toString();
    }
}
