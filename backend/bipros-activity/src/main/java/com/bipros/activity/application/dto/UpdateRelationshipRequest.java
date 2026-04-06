package com.bipros.activity.application.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRelationshipRequest(
    @NotBlank(message = "Relationship type is required") String relationshipType,
    Double lag) {}
