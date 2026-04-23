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
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity(name = "ResourceRole")
@Table(
    name = "resource_roles",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_role_code",
            columnNames = {"code"})
    },
    indexes = {
        @Index(name = "idx_role_resource_type", columnList = "resource_type")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {

  @Column(nullable = false, length = 50, unique = true)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 500)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "resource_type", nullable = false, length = 20)
  private ResourceType resourceType;

  @Column(name = "default_rate", precision = 19, scale = 4)
  private BigDecimal defaultRate;

  @Column(name = "default_units_per_time")
  private Double defaultUnitsPerTime;

  @Column(name = "sort_order", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
  @Default
  private Integer sortOrder = 0;

  // ─── Unit-Rate-Master extensions (Daily Cost Report, Section A) ───
  // These let a Manpower role carry budgeted & actual day-rates directly so the Unit Rate Master
  // view can render "General Labourer / Day / 450 / 470 / +20 / 4.4%" the same way Resource does
  // for Equipment/Material/Sub-Contract.

  /** Unit label for the rate (e.g. "Day", "Hour"). */
  @Column(name = "rate_unit", length = 20)
  private String rateUnit;

  @Column(name = "budgeted_rate", precision = 19, scale = 4)
  private BigDecimal budgetedRate;

  @Column(name = "actual_rate", precision = 19, scale = 4)
  private BigDecimal actualRate;

  /** Free-text bucket for remarks column in the Unit Rate Master (e.g. "Over budget"). */
  @Column(name = "rate_remarks", length = 500)
  private String rateRemarks;
}
