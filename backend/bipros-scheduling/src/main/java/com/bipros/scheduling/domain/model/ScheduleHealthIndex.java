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

import java.util.UUID;

@Entity
@Table(name = "schedule_health_indices", schema = "scheduling")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleHealthIndex extends BaseEntity {

  @Column(name = "schedule_result_id", nullable = false)
  private UUID scheduleResultId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "total_activities", nullable = false)
  private Integer totalActivities;

  @Column(name = "critical_activities", nullable = false)
  private Integer criticalActivities;

  @Column(name = "near_critical_activities", nullable = false)
  private Integer nearCriticalActivities;

  @Column(name = "total_float_average", nullable = false)
  private Double totalFloatAverage;

  @Column(name = "health_score", nullable = false)
  private Double healthScore;

  @Column(name = "float_distribution", columnDefinition = "TEXT")
  private String floatDistribution;

  @Column(name = "risk_level", nullable = false)
  @Enumerated(EnumType.STRING)
  private RiskLevel riskLevel;
}
