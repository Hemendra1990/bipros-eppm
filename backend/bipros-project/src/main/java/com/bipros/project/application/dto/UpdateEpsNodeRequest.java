package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateEpsNodeRequest(
    @NotBlank(message = "Name is required")
    String name,

    UUID obsId,

    Integer sortOrder,

    /** For WBS updates: moves the node to a new parent. Reject self- and descendant-cycles. */
    UUID parentId,

    /** FK to cost.cost_accounts.id — links this WBS element to a cost account. */
    UUID costAccountId,

    /** Budget allocated to this WBS node in crores (INR). */
    BigDecimal budgetCrores
) {
    /** Legacy constructor for callers that predate the parentId field. */
    public UpdateEpsNodeRequest(String name, UUID obsId, Integer sortOrder) {
        this(name, obsId, sortOrder, null, null, null);
    }

    /** Legacy constructor for callers that predate the costAccountId field. */
    public UpdateEpsNodeRequest(String name, UUID obsId, Integer sortOrder, UUID parentId) {
        this(name, obsId, sortOrder, parentId, null, null);
    }

    /** Legacy constructor for callers that predate the budgetCrores field. */
    public UpdateEpsNodeRequest(String name, UUID obsId, Integer sortOrder, UUID parentId, UUID costAccountId) {
        this(name, obsId, sortOrder, parentId, costAccountId, null);
    }
}
