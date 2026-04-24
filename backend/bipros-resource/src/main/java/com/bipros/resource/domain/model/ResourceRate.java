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

  /**
   * IC-PMS M8 rate type. Free text for backward compat; canonical values include
   * {@code CPWD_SOR} (CPWD Schedule of Rates 2024-25) and {@code OVERTIME}.
   */
  @Column(name = "rate_type", nullable = false, length = 50)
  private String rateType;

  @Column(name = "price_per_unit", nullable = false, precision = 19, scale = 4)
  private BigDecimal pricePerUnit;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  @Column(name = "max_units_per_time")
  private Double maxUnitsPerTime;

  // ── PMS MasterData Screen 05 (Unit Rate Master) fields ───────────────────

  /**
   * Internal budgeted cost rate approved at project baseline. When only {@link #pricePerUnit}
   * is supplied by a legacy client we copy it here so analytics reports work without a back-fill.
   */
  @Column(name = "budgeted_rate", precision = 19, scale = 4)
  private BigDecimal budgetedRate;

  /**
   * Current market or invoice rate being paid on site. Compared with {@link #budgetedRate} to
   * produce the Variance column on the Unit Rate Master register.
   */
  @Column(name = "actual_rate", precision = 19, scale = 4)
  private BigDecimal actualRate;

  /** End of the effective window; {@code null} means the rate is still active. */
  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  /** User who approved this rate. FK into security.users, nullable for system-imported rows. */
  @Column(name = "approved_by_user_id")
  private UUID approvedByUserId;

  /** Denormalised user display name so exports work without a join. */
  @Column(name = "approved_by_name", length = 120)
  private String approvedByName;

  /** Screen 05 Resource Category tab — EQUIPMENT / MANPOWER / MATERIAL / SUB_CONTRACT. */
  @Enumerated(EnumType.STRING)
  @Column(name = "category", length = 20)
  private UnitRateCategory category;
}
