package com.bipros.ai.activity.dto;

import java.util.List;
import java.util.UUID;

public record ActivityAiApplyResponse(
        List<String> codeCollisions,
        List<String> wbsResolutionFailures,
        List<String> relationshipResolutionFailures,
        List<UUID> createdActivityIds,
        List<UUID> createdRelationshipIds
) {
}
