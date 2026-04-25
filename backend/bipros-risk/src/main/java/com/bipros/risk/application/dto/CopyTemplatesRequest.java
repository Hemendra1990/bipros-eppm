package com.bipros.risk.application.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record CopyTemplatesRequest(
    @NotEmpty(message = "templateIds must not be empty")
    List<UUID> templateIds
) {}
