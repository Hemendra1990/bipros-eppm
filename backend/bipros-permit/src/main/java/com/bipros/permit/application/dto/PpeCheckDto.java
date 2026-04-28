package com.bipros.permit.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record PpeCheckDto(
        UUID id,
        @NotNull UUID ppeItemTemplateId,
        String ppeItemCode,
        String ppeItemName,
        boolean confirmed,
        UUID confirmedBy,
        Instant confirmedAt
) {}
