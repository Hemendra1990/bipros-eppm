package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.Material;
import com.bipros.resource.domain.model.MaterialCategory;
import com.bipros.resource.domain.model.MaterialStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MaterialResponse(
    UUID id,
    UUID projectId,
    String code,
    String name,
    MaterialCategory category,
    String unit,
    String specificationGrade,
    BigDecimal minStockLevel,
    BigDecimal reorderQuantity,
    Integer leadTimeDays,
    String storageLocation,
    UUID approvedSupplierId,
    MaterialStatus status,
    List<UUID> applicableBoqItemIds
) {
    public static MaterialResponse from(Material m, List<UUID> boqItemIds) {
        return new MaterialResponse(
            m.getId(), m.getProjectId(), m.getCode(), m.getName(),
            m.getCategory(), m.getUnit(), m.getSpecificationGrade(),
            m.getMinStockLevel(), m.getReorderQuantity(), m.getLeadTimeDays(),
            m.getStorageLocation(), m.getApprovedSupplierId(), m.getStatus(),
            boqItemIds != null ? boqItemIds : List.of()
        );
    }
}
