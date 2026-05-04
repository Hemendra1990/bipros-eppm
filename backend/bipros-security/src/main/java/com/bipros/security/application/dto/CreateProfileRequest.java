package com.bipros.security.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateProfileRequest(
        @NotBlank @Size(max = 60) String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 60) String legacyRoleName,
        @NotNull Set<String> permissions
) {}
