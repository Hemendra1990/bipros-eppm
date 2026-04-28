package com.bipros.analytics.application.dto;

import java.util.List;
import java.util.Map;

/**
 * Response from {@code AnalyticsAssistantService.handle()}. Status mirrors
 * {@code AnalyticsAuditLog.Status} and tells the frontend how to render
 * (success → table; refusal/not-configured → narrative-only with a CTA).
 */
public record AnalyticsAssistantResponse(
        String narrative,
        String toolUsed,
        List<String> columns,
        List<Map<String, Object>> rows,
        String sqlExecuted,
        Integer tokensInput,
        Integer tokensOutput,
        Long costMicros,
        String status
) {}
