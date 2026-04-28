package com.bipros.permit.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record PpeItemTemplateDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name,
        String iconKey,
        boolean mandatory,
        int sortOrder
) {}
