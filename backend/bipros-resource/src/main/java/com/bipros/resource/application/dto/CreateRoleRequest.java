package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceType;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Either {@code resourceTypeDefId} (preferred) or the legacy {@code resourceType} enum must be
 * provided. When only the enum is supplied the service resolves the seeded system-default def.
 */
public record CreateRoleRequest(
    @NotBlank(message = "Code is required") String code,
    @NotBlank(message = "Name is required") String name,
    String description,
    UUID resourceTypeDefId,
    ResourceType resourceType,
    BigDecimal defaultRate,
    String rateUnit,
    BigDecimal budgetedRate,
    BigDecimal actualRate,
    String rateRemarks) {}
