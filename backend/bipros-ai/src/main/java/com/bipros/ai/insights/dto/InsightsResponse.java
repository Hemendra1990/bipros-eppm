package com.bipros.ai.insights.dto;

import java.util.List;

public record InsightsResponse(
    String summary,
    List<InsightHighlight> highlights,
    List<InsightVariance> variances,
    List<InsightRecommendation> recommendations,
    List<InsightFinding> findings,
    String rationale,
    String mdx,
    List<ChartSpec> charts
) {}
