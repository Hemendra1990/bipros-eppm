package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * What a resource <em>does</em> — Bulldozer Operator, Cement, Senior Welder, etc. Each role
 * belongs to exactly one {@link ResourceType}.
 */
@Entity
@Table(
    name = "resource_roles",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_resource_role_code", columnNames = {"code"})
    },
    indexes = {
        @Index(name = "idx_resource_role_type", columnList = "resource_type_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceRole extends BaseEntity {

  @Column(nullable = false, length = 50, unique = true)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 500)
  private String description;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "resource_type_id", nullable = false)
  private ResourceType resourceType;

  @Column(name = "productivity_unit", length = 50)
  private String productivityUnit;

  @Column(name = "default_rate", precision = 19, scale = 4)
  private BigDecimal defaultRate;

  @Column(name = "sort_order", nullable = false)
  @Default
  private Integer sortOrder = 0;

  @Column(nullable = false)
  @Default
  private Boolean active = true;
}
