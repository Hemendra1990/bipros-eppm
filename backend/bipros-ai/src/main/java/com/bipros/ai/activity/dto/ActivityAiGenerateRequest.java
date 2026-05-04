package com.bipros.ai.activity.dto;

public record ActivityAiGenerateRequest(
        String projectTypeHint,
        String additionalContext,
        Integer targetActivityCount,
        Double defaultDurationDays
) {
}
