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
 * Approved borrow area / quarry / bitumen depot / cement source per PMS MasterData Screen 08.
 * Soil / aggregate properties (CBR, MDD) are denormalised onto the source so dashboards can
 * render without joining the underlying lab-test table.
 */
@Entity
@Table(
    name = "material_source",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_material_source_project_code",
            columnNames = {"project_id", "source_code"})
    },
    indexes = {
        @Index(name = "idx_material_source_project", columnList = "project_id"),
        @Index(name = "idx_material_source_type", columnList = "source_type"),
        @Index(name = "idx_material_source_lab_status", columnList = "lab_test_status")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialSource extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** Auto-generated source code; prefix determined by {@link #sourceType} (BA-/QRY-/BD-/CEM-). */
    @Column(name = "source_code", nullable = false, length = 30)
    private String sourceCode;

    @Column(name = "name", length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private MaterialSourceType sourceType;

    @Column(name = "village", length = 150)
    private String village;

    @Column(name = "taluk", length = 100)
    private String taluk;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "state", length = 80)
    private String state;

    /** One-way distance from project centreline in km — drives haulage cost. */
    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "approved_quantity", precision = 18, scale = 3)
    private BigDecimal approvedQuantity;

    /** Free-text unit of the approved quantity (e.g. "MT", "CU_M"). */
    @Column(name = "approved_quantity_unit", length = 20)
    private String approvedQuantityUnit;

    /** Environmental / revenue clearance reference number. */
    @Column(name = "approval_reference", length = 200)
    private String approvalReference;

    @Column(name = "approval_authority", length = 200)
    private String approvalAuthority;

    /** Average CBR % from borrow-material lab tests. */
    @Column(name = "cbr_average_percent", precision = 6, scale = 2)
    private BigDecimal cbrAveragePercent;

    /** Maximum Dry Density (g/cc) from IS 2720 Pt.7 Proctor tests. */
    @Column(name = "mdd_gcc", precision = 6, scale = 3)
    private BigDecimal mddGcc;

    @Enumerated(EnumType.STRING)
    @Column(name = "lab_test_status", length = 30)
    private LabTestStatus labTestStatus;
}
