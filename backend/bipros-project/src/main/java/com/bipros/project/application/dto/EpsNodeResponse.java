package com.bipros.project.application.dto;

import java.util.List;
import java.util.UUID;

public record EpsNodeResponse(
    UUID id,
    String code,
    String name,
    UUID parentId,
    UUID obsId,
    Integer sortOrder,
    List<EpsNodeResponse> children
) {
}
