package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "resource_rates",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_resource_rate_type_date",
            columnNames = {"resource_id", "rate_type", "effective_date"})
    },
    indexes = {
        @Index(name = "idx_rate_resource_id", columnList = "resource_id"),
        @Index(name = "idx_rate_type", columnList = "rate_type"),
        @Index(name = "idx_rate_effective_date", columnList = "effective_date")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceRate extends BaseEntity {

  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Column(name = "rate_type", nullable = false, length = 50)
  private String rateType;

  @Column(name = "price_per_unit", nullable = false, precision = 19, scale = 4)
  private BigDecimal pricePerUnit;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  @Column(name = "max_units_per_time")
  private Double maxUnitsPerTime;
}
