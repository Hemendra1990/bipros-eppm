package com.bipros.ai.wbs.dto;

import com.bipros.project.domain.model.AssetClass;

public record WbsAiGenerateFromDocumentRequest(
        AssetClass assetClass,
        Integer targetDepth
) {
}
