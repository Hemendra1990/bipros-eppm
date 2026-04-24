package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceOwnership;
import com.bipros.resource.domain.model.ResourceStatus;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.ResourceUnit;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Create / update payload for Resource Master. The original {@code code} is now optional — when
 * left blank and {@code resourceType = EQUIPMENT}, the service auto-generates {@code EQ-NNN}
 * per PMS MasterData Screen 04.
 */
public record CreateResourceRequest(
    String code,
    @NotBlank(message = "Name is required") String name,
    @NotNull(message = "Resource type is required") ResourceType resourceType,
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
