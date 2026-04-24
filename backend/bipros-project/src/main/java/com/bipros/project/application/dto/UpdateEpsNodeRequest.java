package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record UpdateEpsNodeRequest(
    @NotBlank(message = "Name is required")
    String name,

    UUID obsId,

    Integer sortOrder,

    /** For WBS updates: moves the node to a new parent. Reject self- and descendant-cycles. */
    UUID parentId
) {
    /** Legacy constructor for callers that predate the parentId field. */
    public UpdateEpsNodeRequest(String name, UUID obsId, Integer sortOrder) {
        this(name, obsId, sortOrder, null);
    }
}
