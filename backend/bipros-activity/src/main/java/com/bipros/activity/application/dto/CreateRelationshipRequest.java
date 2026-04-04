package com.bipros.activity.application.dto;

import com.bipros.activity.domain.model.RelationshipType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateRelationshipRequest(
    @NotNull(message = "Predecessor activity ID is required")
    UUID predecessorActivityId,

    @NotNull(message = "Successor activity ID is required")
    UUID successorActivityId,

    RelationshipType relationshipType,

    Double lag
) {}
