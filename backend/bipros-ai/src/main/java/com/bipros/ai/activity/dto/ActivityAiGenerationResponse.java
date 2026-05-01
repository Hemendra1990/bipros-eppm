package com.bipros.ai.activity.dto;

import java.util.List;

public record ActivityAiGenerationResponse(
        String rationale,
        List<ActivityAiNode> activities
) {
}
