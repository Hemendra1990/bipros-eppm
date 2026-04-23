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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
    name = "productivity_norms",
    schema = "resource",
    indexes = {
        @Index(name = "idx_prod_norm_type", columnList = "norm_type"),
        @Index(name = "idx_prod_norm_activity", columnList = "activity_name")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductivityNorm extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "norm_type", nullable = false, length = 20)
  private ProductivityNormType normType;

  @Column(name = "activity_name", nullable = false, length = 150)
  private String activityName;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  /** Manpower: output per man per day; Equipment: not used. */
  @Column(name = "output_per_man_per_day", precision = 12, scale = 4)
  private BigDecimal outputPerManPerDay;

  /** Equipment: output per hour. */
  @Column(name = "output_per_hour", precision = 12, scale = 4)
  private BigDecimal outputPerHour;

  /** Manpower: number of men in a gang. Equipment: not used. */
  @Column(name = "crew_size")
  private Integer crewSize;

  /** Gang or equipment daily output; for manpower = outputPerManPerDay × crewSize. */
  @Column(name = "output_per_day", precision = 12, scale = 4)
  private BigDecimal outputPerDay;

  @Column(name = "working_hours_per_day")
  private Double workingHoursPerDay;

  @Column(name = "fuel_litres_per_hour", precision = 10, scale = 2)
  private BigDecimal fuelLitresPerHour;

  /** Equipment only: e.g. "JCB 210 (1.0 Cum Bucket)". */
  @Column(name = "equipment_spec", length = 150)
  private String equipmentSpec;

  @Column(name = "remarks", length = 500)
  private String remarks;
}
