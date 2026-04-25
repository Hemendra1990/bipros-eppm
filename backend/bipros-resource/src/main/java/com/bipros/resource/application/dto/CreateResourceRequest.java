package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceOwnership;
import com.bipros.resource.domain.model.ResourceStatus;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.ResourceUnit;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Create / update payload for Resource Master. The original {@code code} is now optional — when
 * left blank, the service auto-generates a code using the chosen Resource Type def's prefix
 * (or the base category default LAB / EQ / MAT).
 *
 * <p>Either {@code resourceTypeDefId} (the new admin-managed lookup id) or the legacy
 * {@code resourceType} enum must be supplied. When only the enum is provided the service
 * resolves the seeded system-default def for that base category.
 */
public record CreateResourceRequest(
    String code,
    @NotBlank(message = "Name is required") String name,
    UUID resourceTypeDefId,
    ResourceType resourceType,
    UUID parentId,
    UUID calendarId,
    String email,
    String phone,
    String title,
    Double maxUnitsPerDay,
    ResourceStatus status,
    Double hourlyRate,
    Double costPerUse,
    Double overtimeRate,
    /** Accepted as either {@code unit} or {@code unitOfMeasure} for client convenience. */
    @JsonAlias({"unitOfMeasure"})
    ResourceUnit unit,

    // ── PMS MasterData Screen 04 (Equipment Master) fields ───────────────────
    @JsonAlias({"capacity", "specification"}) String capacitySpec,
    @JsonAlias({"make", "model"}) String makeModel,
    Integer quantityAvailable,
    ResourceOwnership ownershipType,
    Double standardOutputPerDay,
    String standardOutputUnit,
    BigDecimal fuelLitresPerHour
) {}
