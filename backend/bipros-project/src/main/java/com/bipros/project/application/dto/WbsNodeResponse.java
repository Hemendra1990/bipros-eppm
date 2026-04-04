package com.bipros.project.application.dto;

import java.util.List;
import java.util.UUID;

public record WbsNodeResponse(
    UUID id,
    String code,
    String name,
    UUID parentId,
    UUID projectId,
    UUID obsNodeId,
    Integer sortOrder,
    Double summaryDuration,
    Double summaryPercentComplete,
    List<WbsNodeResponse> children
) {
}
