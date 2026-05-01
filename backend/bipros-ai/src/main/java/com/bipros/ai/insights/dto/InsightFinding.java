package com.bipros.ai.insights.dto;

public record InsightFinding(
    String label,
    String detail,
    String severity // info|warning|critical
) {}
