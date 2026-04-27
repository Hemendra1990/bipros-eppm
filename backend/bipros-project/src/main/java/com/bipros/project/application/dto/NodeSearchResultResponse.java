package com.bipros.project.application.dto;

import java.util.List;
import java.util.UUID;

public record NodeSearchResultResponse(
    UUID id,
    String code,
    String name,
    UUID parentId,
    List<UUID> ancestorIds,
    String pathLabel
) {
}
