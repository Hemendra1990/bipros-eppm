package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Create / update payload for a Resource. {@code code} may be blank — the service auto-generates
 * one based on the type code (LAB / EQ / MAT). Exactly one of the per-type detail blocks
 * ({@link #equipment}, {@link #material}, {@link #manpower}) is allowed, and only the one matching
 * the type's base category will be persisted.
 */
public record CreateResourceRequest(
    String code,
    @NotBlank(message = "Name is required") String name,
    String description,
    @NotNull(message = "roleId is required") UUID roleId,
    @NotNull(message = "resourceTypeId is required") UUID resourceTypeId,
    BigDecimal availability,
    BigDecimal costPerUnit,
    String unit,
    ResourceStatus status,
    UUID calendarId,
    UUID parentId,
    UUID userId,
    Integer sortOrder,

    @Valid EquipmentDetailsDto equipment,
    @Valid MaterialDetailsDto material,
    @Valid ManpowerDto manpower
) {}
