package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Flat lookup of manpower grades (A, B, C, ...). Used as one dimension of the
 * Manpower Rate Master key (role + category + sub-category + grade).
 */
@Entity
@Table(
    name = "grade_master",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_grade_master_code", columnNames = {"code"})
    },
    indexes = {
        @Index(name = "idx_grade_master_active_sort", columnList = "active, sort_order")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeMaster extends BaseEntity {

  @Column(nullable = false, length = 20)
  private String code;

  @Column(nullable = false, length = 100)
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
