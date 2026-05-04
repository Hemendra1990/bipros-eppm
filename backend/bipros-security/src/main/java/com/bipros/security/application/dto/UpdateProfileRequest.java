package com.bipros.security.application.dto;

import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateProfileRequest(
        @Size(max = 120) String name,
        @Size(max = 500) String description,
        @Size(max = 60) String legacyRoleName,
        Set<String> permissions
) {}
