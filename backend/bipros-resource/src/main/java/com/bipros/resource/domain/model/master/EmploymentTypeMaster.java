package com.bipros.resource.domain.model.master;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Master for Manpower Employment Type — Permanent / Contract / Daily Wage / Sub-Contract /
 * Casual / admin-defined. Stored on {@code ManpowerMaster.employmentType} as the master row's
 * {@code name} (string), keeping the existing column without any FK migration.
 */
@Entity
@Table(
    name = "employment_type_master",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_employment_type_master_code", columnNames = {"code"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmploymentTypeMaster extends BaseEntity {

  @Column(nullable = false, length = 50, unique = true)
  private String code;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(name = "sort_order", nullable = false)
  @Default
  private Integer sortOrder = 0;

  @Column(nullable = false)
  @Default
  private Boolean active = true;
}
