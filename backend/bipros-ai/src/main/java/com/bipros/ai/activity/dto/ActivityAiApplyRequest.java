package com.bipros.ai.activity.dto;

import java.util.List;

public record ActivityAiApplyRequest(
        List<ActivityAiNode> activities
) {
}
