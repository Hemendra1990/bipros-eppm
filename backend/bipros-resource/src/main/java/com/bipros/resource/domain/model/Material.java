package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Material catalogue entry per PMS MasterData Screen 09a. Owns the reorder parameters
 * (min stock, reorder qty, lead time) consumed by {@link MaterialStock} and the reorder-alert
 * report.
 */
@Entity
@Table(
    name = "material",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_material_project_code",
            columnNames = {"project_id", "code"})
    },
    indexes = {
        @Index(name = "idx_material_project", columnList = "project_id"),
        @Index(name = "idx_material_category", columnList = "category"),
        @Index(name = "idx_material_supplier", columnList = "approved_supplier_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Material extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** Auto-generated catalogue code {@code MAT-NNN}. Unique per project. */
    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30)
    private MaterialCategory category;

    /** Free-text unit of measure (e.g. "MT", "CU_M", "NOS"). */
    @Column(name = "unit", length = 20)
    private String unit;

    /** Technical grade / standard reference (e.g. "VG-30", "OPC 43", "Fe500D"). */
    @Column(name = "specification_grade", length = 120)
    private String specificationGrade;

    @Column(name = "min_stock_level", precision = 18, scale = 3)
    private BigDecimal minStockLevel;

    @Column(name = "reorder_quantity", precision = 18, scale = 3)
    private BigDecimal reorderQuantity;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "storage_location", length = 120)
    private String storageLocation;

    /** FK into admin.organisations (type = SUPPLIER). */
    @Column(name = "approved_supplier_id")
    private UUID approvedSupplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private MaterialStatus status;
}
