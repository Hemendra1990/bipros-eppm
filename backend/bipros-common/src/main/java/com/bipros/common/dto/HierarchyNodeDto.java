package com.bipros.common.dto;

import java.util.List;
import java.util.UUID;

/**
 * Generic DTO for tree hierarchy nodes (EPS, OBS, WBS, Cost Accounts).
 * Children are eagerly populated for tree rendering.
 */
public record HierarchyNodeDto(
        UUID id,
        UUID parentId,
        String code,
        String name,
        int sortOrder,
        List<HierarchyNodeDto> children
) {}
