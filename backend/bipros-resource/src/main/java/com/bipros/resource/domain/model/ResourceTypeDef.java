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

/**
 * Admin-managed Resource Type definition. Ships with three system-default rows for the 3M's
 * (Manpower / Material / Machine) and lets admins add custom types — e.g. SUBCONTRACTOR, TOOL —
 * each tagged with a {@link ResourceType} base category that drives cost reporting, code-prefix
 * generation and the conditional equipment-only fields on the new-resource form.
 */
@Entity
@Table(
    name = "resource_type_defs",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_resource_type_def_code", columnNames = {"code"})
    },
    indexes = {
        @Index(name = "idx_resource_type_def_active_sort", columnList = "active, sort_order"),
        @Index(name = "idx_resource_type_def_base_category", columnList = "base_category")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceTypeDef extends BaseEntity {

  @Column(nullable = false, length = 50, unique = true)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  /**
   * One of {@code LABOR / NONLABOR / MATERIAL}. Determines how downstream code (cost reporting,
   * unit-rate buckets, equipment-only form fields) treats resources of this type.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "base_category", nullable = false, length = 20)
  private ResourceType baseCategory;

  /**
   * Optional override for the auto-generated resource code prefix. When null, the service falls
   * back to the base category default (LAB / EQ / MAT).
   */
  @Column(name = "code_prefix", length = 10)
  private String codePrefix;

  @Column(name = "sort_order")
  private Integer sortOrder;

  @Column(nullable = false)
  @Default
  private Boolean active = true;

  /**
   * True for the seeded 3M defaults. The service blocks delete and rejects mutations to
   * {@link #code} / {@link #baseCategory} on these rows.
   */
  @Column(name = "system_default", nullable = false)
  @Default
  private Boolean systemDefault = false;
}
