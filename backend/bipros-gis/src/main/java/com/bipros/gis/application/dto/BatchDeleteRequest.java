package com.bipros.gis.application.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BatchDeleteRequest(
    @NotEmpty(message = "At least one id is required")
    List<UUID> ids
) {}
