package com.bipros.permit.application.dto;

import com.bipros.permit.domain.model.WorkerRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PermitWorkerDto(
        UUID id,
        @NotBlank String fullName,
        String civilId,
        String nationality,
        String trade,
        @NotNull WorkerRole roleOnPermit,
        String trainingCertsJson
) {}
