package com.bipros.ai.insights.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record ChartSpec(
    String id,
    String title,
    String type,
    JsonNode option,
    String note
) {}
