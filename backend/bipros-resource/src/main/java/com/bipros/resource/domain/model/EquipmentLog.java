package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "equipment_logs",
    schema = "resource",
    indexes = {
        @Index(name = "idx_equipment_log_resource_id", columnList = "resource_id"),
        @Index(name = "idx_equipment_log_project_id", columnList = "project_id"),
        @Index(name = "idx_equipment_log_date", columnList = "log_date"),
        @Index(name = "idx_equipment_log_status", columnList = "status")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentLog extends BaseEntity {

  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "log_date", nullable = false)
  private LocalDate logDate;

  @Column(name = "deployment_site", length = 200)
  private String deploymentSite;

  @Column(name = "operating_hours")
  private Double operatingHours;

  @Column(name = "idle_hours")
  private Double idleHours;

  @Column(name = "breakdown_hours")
  private Double breakdownHours;

  @Column(name = "fuel_consumed")
  private Double fuelConsumed;

  @Column(name = "operator_name", length = 100)
  private String operatorName;

  @Column(name = "remarks", columnDefinition = "text")
  private String remarks;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'WORKING'")
  @Default
  private EquipmentStatus status = EquipmentStatus.WORKING;
}
