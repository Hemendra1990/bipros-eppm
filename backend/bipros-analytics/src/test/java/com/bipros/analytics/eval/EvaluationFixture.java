package com.bipros.analytics.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * One YAML eval fixture. The shape is intentionally tiny so non-engineers can
 * author/review a regression set without learning Java. See {@code EvaluationSuiteTest}
 * for how the {@code expect} block is matched against
 * {@code AnalyticsAssistantResponse}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluationFixture(
        String id,
        String category,
        String queryText,
        UUID projectContextId,
        Expect expect
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Expect(
            /** {@code success} or {@code refusal}. */
            String kind,
            /** Tool we expect the LLM to pick, or "any". Optional. */
            String toolCalled,
            /** Required minimum number of result rows for {@code kind=success}. Default 0. */
            Integer minRows,
            /** When {@code kind=refusal}, the expected status string ({@code REFUSED}). Optional. */
            String refusalKind,
            /** Substring assertions on the narrative. Optional. */
            List<String> narrativeContains
    ) {}
}
