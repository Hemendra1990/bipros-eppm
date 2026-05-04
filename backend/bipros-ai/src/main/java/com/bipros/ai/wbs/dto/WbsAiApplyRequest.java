package com.bipros.ai.wbs.dto;

import java.util.List;
import java.util.UUID;

public record WbsAiApplyRequest(
        UUID parentId,
        ApplyMode mode,
        List<WbsAiNode> nodes
) {
    /** Back-compat constructor: defaults mode to MERGE when omitted. */
    public WbsAiApplyRequest(UUID parentId, List<WbsAiNode> nodes) {
        this(parentId, ApplyMode.MERGE, nodes);
    }
}
