package com.bipros.ai.activity.dto;

import com.bipros.ai.wbs.dto.CollisionResult;

import java.util.List;

public record ActivityAiGenerationResponse(
        String rationale,
        List<ActivityAiNode> activities,
        /** Per-activity dry-run annotations (what apply would do for each generated row). */
        List<CollisionResult> previewAnnotations
) {
    /** Back-compat: callers that don't compute annotations pass null. */
    public ActivityAiGenerationResponse(String rationale, List<ActivityAiNode> activities) {
        this(rationale, activities, null);
    }
}
