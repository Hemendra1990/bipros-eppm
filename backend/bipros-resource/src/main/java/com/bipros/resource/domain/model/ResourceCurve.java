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

@Entity
@Table(
    name = "resource_curves",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_resource_curve_name",
            columnNames = {"name"})
    },
    indexes = {
        @Index(name = "idx_curve_is_default", columnList = "is_default")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceCurve extends BaseEntity {

  @Column(nullable = false, length = 100, unique = true)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(name = "is_default", nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  @Default
  private Boolean isDefault = false;

  @Column(name = "curve_data", columnDefinition = "text", nullable = false)
  private String curveData;
}
