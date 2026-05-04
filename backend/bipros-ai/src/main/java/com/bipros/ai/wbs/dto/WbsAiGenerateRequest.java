package com.bipros.ai.wbs.dto;

import com.bipros.project.domain.model.AssetClass;

public record WbsAiGenerateRequest(
        AssetClass assetClass,
        String projectTypeHint,
        String additionalContext,
        Integer targetDepth
) {
}
