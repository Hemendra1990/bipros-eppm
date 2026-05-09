package com.bipros.resource.domain.model.rate;

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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Material Rate Master — one row per priceable material variant identified by
 * (Material Category, Spec/Grade). Spec/Grade is free text (e.g. "OPC 53",
 * "12mm rebar"). Carries the productivity unit and cost per unit that flow into
 * every Material {@code Resource} pointing at this row.
 */
@Entity
@Table(
    name = "material_rate_masters",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_material_rate_master_key",
            columnNames = {"category_id", "spec_grade"})
    },
    indexes = {
        @Index(name = "idx_material_rate_master_category", columnList = "category_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialRateMaster extends BaseEntity {

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(name = "spec_grade", nullable = false, length = 200)
  private String specGrade;

  @Column(nullable = false, length = 30)
  private String unit;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal rate;

  @Column(nullable = false)
  @Default
  private Boolean active = true;
}
