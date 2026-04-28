package com.bipros.permit.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PermitPackDto(
        UUID id,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String name,
        String description,
        boolean active,
        int sortOrder
) {}
