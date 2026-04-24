package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.MaterialCategory;
import com.bipros.resource.domain.model.MaterialStatus;
import com.bipros.resource.domain.model.ResourceUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Create / update payload for Material Catalogue (Screen 09a). {@code code} may be blank —
 * the service auto-generates {@code MAT-NNN}.
 */
public record CreateMaterialRequest(
    @Size(max = 30) String code,
    @NotBlank @Size(max = 150) String name,
    @NotNull MaterialCategory category,
    ResourceUnit unit,
    @Size(max = 120) String specificationGrade,
    @PositiveOrZero BigDecimal minStockLevel,
    @PositiveOrZero BigDecimal reorderQuantity,
    @PositiveOrZero Integer leadTimeDays,
    @Size(max = 120) String storageLocation,
    UUID approvedSupplierId,
    MaterialStatus status,
    List<UUID> applicableBoqItemIds
) {
}
