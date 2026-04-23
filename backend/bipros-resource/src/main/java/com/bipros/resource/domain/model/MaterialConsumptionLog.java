package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "material_consumption_logs",
    schema = "resource",
    indexes = {
        @Index(name = "idx_mcl_project_date", columnList = "project_id, log_date"),
        @Index(name = "idx_mcl_resource", columnList = "resource_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialConsumptionLog extends BaseEntity {

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "log_date", nullable = false)
  private LocalDate logDate;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(name = "material_name", nullable = false, length = 150)
  private String materialName;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "opening_stock", nullable = false, precision = 18, scale = 3)
  private BigDecimal openingStock;

  @Column(name = "received", nullable = false, precision = 18, scale = 3,
      columnDefinition = "NUMERIC(18,3) DEFAULT 0")
  private BigDecimal received;

  @Column(name = "consumed", nullable = false, precision = 18, scale = 3,
      columnDefinition = "NUMERIC(18,3) DEFAULT 0")
  private BigDecimal consumed;

  @Column(name = "closing_stock", nullable = false, precision = 18, scale = 3)
  private BigDecimal closingStock;

  @Column(name = "wastage_percent", precision = 5, scale = 2)
  private BigDecimal wastagePercent;

  @Column(name = "issued_by", length = 150)
  private String issuedBy;

  @Column(name = "received_by", length = 150)
  private String receivedBy;

  @Column(name = "wbs_node_id")
  private UUID wbsNodeId;

  @Column(name = "remarks", length = 500)
  private String remarks;
}
