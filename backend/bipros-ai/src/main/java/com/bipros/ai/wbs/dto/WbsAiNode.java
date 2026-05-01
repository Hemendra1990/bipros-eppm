package com.bipros.ai.wbs.dto;

import java.util.List;

public record WbsAiNode(
        String code,
        String name,
        String description,
        List<WbsAiNode> children
) {
}
