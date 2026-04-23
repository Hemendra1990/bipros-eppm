package com.bipros.project.application.dto;

import com.bipros.project.domain.model.DeploymentResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.UUID;

public record CreateDailyResourceDeploymentRequest(
    @NotNull LocalDate logDate,

    @NotNull DeploymentResourceType resourceType,

    @NotBlank String resourceDescription,

    UUID resourceId,

    UUID resourceRoleId,

    @PositiveOrZero Integer nosPlanned,

    @PositiveOrZero Integer nosDeployed,

    @PositiveOrZero Double hoursWorked,

    @PositiveOrZero Double idleHours,

    String remarks
) {}
