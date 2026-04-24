package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Goods Receipt Note per PMS MasterData Screen 09c-draft. Each GRN increments the corresponding
 * {@link MaterialStock} row and feeds the "Received (Month)" column on the Stock Register.
 */
@Entity
@Table(
    name = "goods_receipt_note",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_grn_number", columnNames = {"grn_number"})
    },
    indexes = {
        @Index(name = "idx_grn_project", columnList = "project_id"),
        @Index(name = "idx_grn_material", columnList = "material_id"),
        @Index(name = "idx_grn_date", columnList = "received_date")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoodsReceiptNote extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** Auto-generated number in {@code GRN-YYYYMM-NNNN} format. */
    @Column(name = "grn_number", nullable = false, length = 30)
    private String grnNumber;

    @Column(name = "material_id", nullable = false)
    private UUID materialId;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_rate", precision = 19, scale = 4)
    private BigDecimal unitRate;

    /** Derived: quantity × unitRate. */
    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    /** FK → admin.organisations (SUPPLIER type). */
    @Column(name = "supplier_organisation_id")
    private UUID supplierOrganisationId;

    @Column(name = "po_number", length = 50)
    private String poNumber;

    @Column(name = "vehicle_number", length = 30)
    private String vehicleNumber;

    @Column(name = "received_by_user_id")
    private UUID receivedByUserId;

    @Column(name = "accepted_quantity", precision = 18, scale = 3)
    private BigDecimal acceptedQuantity;

    @Column(name = "rejected_quantity", precision = 18, scale = 3)
    private BigDecimal rejectedQuantity;

    @Column(name = "remarks", length = 500)
    private String remarks;
}
