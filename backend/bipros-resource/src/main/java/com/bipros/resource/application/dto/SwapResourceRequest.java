package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SwapResourceRequest(
    @NotNull(message = "Resource ID is required") UUID resourceId,
    Boolean override) {}
