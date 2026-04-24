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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Aggregate stock state per (project, material). Maintained by the GRN and Issue services so
 * the Screen 09b Stock Register reads a single row per material. {@link #stockStatusTag} is
 * derived from currentStock vs the Material's minStockLevel and updated on every write.
 */
@Entity
@Table(
    name = "material_stock",
    schema = "resource",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_material_stock_project_material",
        columnNames = {"project_id", "material_id"}),
    indexes = {
        @Index(name = "idx_stock_project", columnList = "project_id"),
        @Index(name = "idx_stock_material", columnList = "material_id"),
        @Index(name = "idx_stock_status", columnList = "stock_status_tag")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialStock extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "material_id", nullable = false)
    private UUID materialId;

    @Column(name = "opening_stock", precision = 18, scale = 3)
    private BigDecimal openingStock;

    @Column(name = "received_month", precision = 18, scale = 3)
    private BigDecimal receivedMonth;

    @Column(name = "issued_month", precision = 18, scale = 3)
    private BigDecimal issuedMonth;

    @Column(name = "current_stock", nullable = false, precision = 18, scale = 3)
    private BigDecimal currentStock;

    @Column(name = "last_grn_id")
    private UUID lastGrnId;

    @Column(name = "last_issue_date")
    private LocalDate lastIssueDate;

    @Column(name = "cumulative_consumed", precision = 18, scale = 3)
    private BigDecimal cumulativeConsumed;

    @Column(name = "wastage_percent", precision = 8, scale = 4)
    private BigDecimal wastagePercent;

    /** {@code currentStock × latestUnitRate} — refreshed on each GRN/issue. */
    @Column(name = "stock_value", precision = 19, scale = 2)
    private BigDecimal stockValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_status_tag", length = 10)
    private StockStatusTag stockStatusTag;
}
