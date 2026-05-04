package com.bipros.ai.insights.dto;

public record InsightRecommendation(
    String title,
    String priority, // low|medium|high
    String action,
    String rationale
) {}
