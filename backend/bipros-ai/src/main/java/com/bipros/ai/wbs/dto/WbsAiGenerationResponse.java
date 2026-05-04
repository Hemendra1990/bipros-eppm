package com.bipros.ai.wbs.dto;

import com.bipros.project.domain.model.AssetClass;

import java.util.List;

public record WbsAiGenerationResponse(
        AssetClass resolvedAssetClass,
        boolean assetClassNeedsConfirmation,
        String rationale,
        List<WbsAiNode> nodes,
        /** Per-node dry-run annotations (what apply would do for each generated node). */
        List<CollisionResult> previewAnnotations
) {
    /** Back-compat constructor: callers that don't yet compute annotations pass null. */
    public WbsAiGenerationResponse(AssetClass resolvedAssetClass, boolean assetClassNeedsConfirmation,
                                    String rationale, List<WbsAiNode> nodes) {
        this(resolvedAssetClass, assetClassNeedsConfirmation, rationale, nodes, null);
    }
}
