package com.bipros.scheduling.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schedule_scenarios", schema = "scheduling")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleScenario extends BaseEntity {

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "scenario_name", nullable = false, length = 100)
  private String scenarioName;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "scenario_type")
  @Enumerated(EnumType.STRING)
  private ScenarioType scenarioType;

  @Column(name = "base_schedule_result_id")
  private UUID baseScheduleResultId;

  @Column(name = "project_duration")
  private Double projectDuration;

  @Column(name = "critical_path_length")
  private Double criticalPathLength;

  @Column(name = "total_cost", precision = 19, scale = 2)
  private BigDecimal totalCost;

  @Column(columnDefinition = "TEXT")
  private String modifiedActivities;

  @Column(name = "status")
  @Enumerated(EnumType.STRING)
  private ScenarioStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
