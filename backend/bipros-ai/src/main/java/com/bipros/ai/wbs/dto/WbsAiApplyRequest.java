package com.bipros.ai.wbs.dto;

import java.util.List;
import java.util.UUID;

public record WbsAiApplyRequest(
        UUID parentId,
        List<WbsAiNode> nodes
) {
}
