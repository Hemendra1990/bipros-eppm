package com.bipros.project.application.dto;

import com.bipros.project.domain.model.WbsStatus;
import com.bipros.project.domain.model.WbsType;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateWbsNodeRequest(
    @NotBlank(message = "Code is required")
    String code,

    @NotBlank(message = "Name is required")
    String name,

    UUID parentId,

    /** Taken from the path when routed via {@code /v1/projects/{projectId}/wbs}. */
    UUID projectId,

    UUID obsNodeId,

    /** Optional; defaults to WORK_PACKAGE when parent is set, NODE otherwise. */
    WbsType wbsType,

    /** Optional; defaults to NOT_STARTED. */
    WbsStatus wbsStatus,

    /** Optional; when omitted the server derives it from the parent's level + 1 (1 for roots). */
    Integer wbsLevel
) {
    /** Legacy constructor kept for callers that predate the taxonomy fields. */
    public CreateWbsNodeRequest(String code, String name, UUID parentId, UUID projectId, UUID obsNodeId) {
        this(code, name, parentId, projectId, obsNodeId, null, null, null);
    }
}
