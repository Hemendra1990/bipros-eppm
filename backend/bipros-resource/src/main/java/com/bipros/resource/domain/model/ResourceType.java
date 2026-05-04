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
 * Top-tier of the Resource taxonomy. Three system-default rows are seeded by Liquibase
 * (LABOR / EQUIPMENT / MATERIAL); admins may add custom types alongside them.
 */
@Entity
@Table(
    name = "resource_types",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_resource_type_code", columnNames = {"code"})
    },
    indexes = {
        @Index(name = "idx_resource_type_active_sort", columnList = "active, sort_order")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceType extends BaseEntity {

  @Column(nullable = false, length = 30, unique = true)
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

  @Column(name = "system_default", nullable = false)
  @Default
  private Boolean systemDefault = false;
}
