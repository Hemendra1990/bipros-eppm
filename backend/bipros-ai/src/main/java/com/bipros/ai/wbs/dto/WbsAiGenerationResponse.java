package com.bipros.ai.wbs.dto;

import com.bipros.project.domain.model.AssetClass;

import java.util.List;

public record WbsAiGenerationResponse(
        AssetClass resolvedAssetClass,
        boolean assetClassNeedsConfirmation,
        String rationale,
        List<WbsAiNode> nodes
) {
}
