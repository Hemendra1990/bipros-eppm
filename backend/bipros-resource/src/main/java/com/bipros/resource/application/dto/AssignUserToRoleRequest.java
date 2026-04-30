package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record AssignUserToRoleRequest(
    @NotNull UUID userId,
    Boolean primary,
    LocalDate assignedFrom,
    LocalDate assignedTo,
    String remarks
) {}
