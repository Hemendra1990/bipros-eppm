package com.bipros.resource.application.dto;

import com.bipros.resource.domain.algorithm.LevelingMode;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ResourceLevelingRequest(
        @NotNull LevelingMode mode,
        List<UUID> resourceIds
) {}
