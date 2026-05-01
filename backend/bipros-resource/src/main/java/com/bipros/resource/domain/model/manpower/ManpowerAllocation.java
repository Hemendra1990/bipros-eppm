package com.bipros.resource.domain.model.manpower;

import com.bipros.resource.domain.model.enums.AvailabilityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "manpower_allocation",
    schema = "resource",
    indexes = {
        @Index(name = "idx_manpower_alloc_project", columnList = "current_project_id"),
        @Index(name = "idx_manpower_alloc_status", columnList = "availability_status")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManpowerAllocation {

  @Id
  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "availability_status", length = 30)
  private AvailabilityStatus availabilityStatus;

  @Column(name = "current_project_id")
  private UUID currentProjectId;

  @Column(name = "assigned_activity_id")
  private UUID assignedActivityId;

  @Column(name = "site_name", length = 120)
  private String siteName;

  @Column(name = "role_in_project", length = 120)
  private String roleInProject;

  @Column(name = "crew_id")
  private UUID crewId;

  @Column(name = "utilization_percentage", precision = 5, scale = 2)
  private BigDecimal utilizationPercentage;

  @Column(name = "standard_output_per_hour", precision = 15, scale = 4)
  private BigDecimal standardOutputPerHour;

  @Column(name = "output_unit", length = 30)
  private String outputUnit;

  @Column(name = "efficiency_factor", precision = 5, scale = 4)
  private BigDecimal efficiencyFactor;

  @Column(name = "performance_rating", precision = 4, scale = 2)
  private BigDecimal performanceRating;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "productivity_trend", columnDefinition = "jsonb")
  private String productivityTrend;

  @Column(name = "attrition_risk_score", precision = 5, scale = 4)
  private BigDecimal attritionRiskScore;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "skill_gap_analysis", columnDefinition = "jsonb")
  private String skillGapAnalysis;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "recommended_training", columnDefinition = "jsonb")
  private String recommendedTraining;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "optimal_assignment", columnDefinition = "jsonb")
  private String optimalAssignment;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @LastModifiedBy
  @Column(name = "updated_by")
  private String updatedBy;

  @Version
  @Column(name = "version")
  private Long version;
}
