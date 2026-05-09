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

/**
 * Equipment Rate Master — one row per priceable equipment variant identified by
 * (Equipment Name, Make, Model). Carries the productivity unit and cost per unit
 * that flow into every Equipment {@code Resource} pointing at this row.
 */
@Entity
@Table(
    name = "equipment_rate_masters",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_equipment_rate_master_key",
            columnNames = {"equipment_name", "make", "model"})
    },
    indexes = {
        @Index(name = "idx_equipment_rate_master_name", columnList = "equipment_name")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentRateMaster extends BaseEntity {

  @Column(name = "equipment_name", nullable = false, length = 200)
  private String equipmentName;

  @Column(nullable = false, length = 100)
  private String make;

  @Column(nullable = false, length = 100)
  private String model;

  @Column(nullable = false, length = 30)
  private String unit;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal rate;

  @Column(nullable = false)
  @Default
  private Boolean active = true;
}
