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

import java.util.UUID;

@Entity
@Table(
    name = "material_reconciliations",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_material_recon_resource_period",
            columnNames = {"resource_id", "period"})
    },
    indexes = {
        @Index(name = "idx_material_recon_resource_id", columnList = "resource_id"),
        @Index(name = "idx_material_recon_project_id", columnList = "project_id"),
        @Index(name = "idx_material_recon_period", columnList = "period"),
        @Index(name = "idx_material_recon_wbs_node_id", columnList = "wbs_node_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialReconciliation extends BaseEntity {

  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "wbs_node_id")
  private UUID wbsNodeId;

  @Column(name = "period", nullable = false, length = 10)
  private String period;

  @Column(name = "opening_balance", nullable = false)
  private Double openingBalance;

  @Column(name = "received", nullable = false)
  private Double received;

  @Column(name = "consumed", nullable = false)
  private Double consumed;

  @Column(name = "wastage", nullable = false, columnDefinition = "double precision default 0")
  private Double wastage;

  @Column(name = "closing_balance", nullable = false)
  private Double closingBalance;

  @Column(name = "unit", length = 20)
  private String unit;

  @Column(name = "remarks", columnDefinition = "text")
  private String remarks;
}
