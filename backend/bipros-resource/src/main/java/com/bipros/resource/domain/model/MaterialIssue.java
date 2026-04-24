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
 * Material issue (challan) per PMS MasterData Screen 09d-draft. Each issue decrements the
 * corresponding {@link MaterialStock} row and feeds the "Issued (Month)" column on the Stock
 * Register as well as the daily consumption log.
 */
@Entity
@Table(
    name = "material_issue",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_material_issue_challan", columnNames = {"challan_number"})
    },
    indexes = {
        @Index(name = "idx_issue_project", columnList = "project_id"),
        @Index(name = "idx_issue_material", columnList = "material_id"),
        @Index(name = "idx_issue_date", columnList = "issue_date")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialIssue extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** Auto-generated number in {@code ISS-YYYYMM-NNNN} format. */
    @Column(name = "challan_number", nullable = false, length = 30)
    private String challanNumber;

    @Column(name = "material_id", nullable = false)
    private UUID materialId;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "issued_to_user_id")
    private UUID issuedToUserId;

    @Column(name = "stretch_id")
    private UUID stretchId;

    @Column(name = "activity_id")
    private UUID activityId;

    @Column(name = "wastage_quantity", precision = 18, scale = 3)
    private BigDecimal wastageQuantity;

    @Column(name = "remarks", length = 500)
    private String remarks;
}
