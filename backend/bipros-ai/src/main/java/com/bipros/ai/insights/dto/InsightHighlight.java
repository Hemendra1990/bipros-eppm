package com.bipros.ai.insights.dto;

public record InsightHighlight(
    String label,
    String value,
    String severity, // info|warning|critical
    String trend     // up|down|flat|null
) {}
