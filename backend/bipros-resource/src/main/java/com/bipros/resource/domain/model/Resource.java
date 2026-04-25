package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.UUID;

@Entity
@Table(
    name = "resources",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_resource_code",
            columnNames = {"code"})
    },
    indexes = {
        @Index(name = "idx_resource_type", columnList = "resource_type"),
        @Index(name = "idx_resource_status", columnList = "status"),
        @Index(name = "idx_resource_parent_id", columnList = "parent_id"),
        @Index(name = "idx_resource_calendar_id", columnList = "calendar_id"),
        @Index(name = "idx_resource_category", columnList = "resource_category"),
        @Index(name = "idx_resource_wbs_assignment", columnList = "wbs_assignment_id"),
        @Index(name = "idx_resource_responsible_contractor", columnList = "responsible_contractor_id"),
        @Index(name = "idx_resource_type_def", columnList = "resource_type_def_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resource extends BaseEntity {

  @Column(nullable = false, length = 50, unique = true)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  /**
   * Denormalised base category, copied from {@link #resourceTypeDef}'s {@code baseCategory} on
   * create/update so existing per-type queries (cost reporting, equipment-only branching) keep
   * working without a join.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "resource_type", nullable = false, length = 20)
  private ResourceType resourceType;

  /**
   * The admin-managed Resource Type definition. Drives display name, optional code prefix and
   * sort order. Nullable for backwards-compatibility with rows seeded before the def lookup
   * existed; the service populates this on every new create/update.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "resource_type_def_id")
  private ResourceTypeDef resourceTypeDef;

  /** IC-PMS M8 detailed category (e.g. EARTH_MOVING, SKILLED_LABOUR, CEMENT). */
  @Enumerated(EnumType.STRING)
  @Column(name = "resource_category", length = 40)
  private ResourceCategory resourceCategory;

  /**
   * Coarse "Unit Rate Master" bucket (Daily Cost Report, Section A): EQUIPMENT / MATERIAL /
   * SUB_CONTRACT. Manpower lives on {@code ResourceRole} instead and is not stored here.
   * Nullable for backwards-compat with existing seeders.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "cost_category", length = 20)
  private CostCategory costCategory;

  /** IC-PMS M8 unit of measure for the pool. */
  @Enumerated(EnumType.STRING)
  @Column(name = "unit", length = 20)
  private ResourceUnit unit;

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(name = "calendar_id")
  private UUID calendarId;

  @Column(length = 100)
  private String email;

  @Column(length = 20)
  private String phone;

  @Column(length = 100)
  private String title;

  @Column(name = "max_units_per_day", nullable = false, columnDefinition = "double precision default 8.0")
  @Default
  private Double maxUnitsPerDay = 8.0;

  @Column(name = "default_units_per_time")
  private Double defaultUnitsPerTime;

  /** IC-PMS M8: total pool capacity (e.g. 15 excavators, 500 MT cement). */
  @Column(name = "pool_max_available")
  private Double poolMaxAvailable;

  /** IC-PMS M8: planned deployment today. */
  @Column(name = "planned_units_today")
  private Double plannedUnitsToday;

  /** IC-PMS M8: actual deployment today. */
  @Column(name = "actual_units_today")
  private Double actualUnitsToday;

  /** IC-PMS M8: actual/planned * 100. OVER_90 triggers warning, CRITICAL_100 alerts. */
  @Column(name = "utilisation_percent")
  private Double utilisationPercent;

  @Enumerated(EnumType.STRING)
  @Column(name = "utilisation_status", length = 30)
  private UtilisationStatus utilisationStatus;

  /** IC-PMS M8: daily cost for today's deployment (₹ Lakh). */
  @Column(name = "daily_cost_lakh", precision = 12, scale = 2)
  private BigDecimal dailyCostLakh;

  /** IC-PMS M8: cumulative cost incurred to date (₹ Crores). */
  @Column(name = "cumulative_cost_crores", precision = 12, scale = 2)
  private BigDecimal cumulativeCostCrores;

  /** IC-PMS M8: assigned WBS package (denorm, matches WbsNode.code). */
  @Column(name = "wbs_assignment_id", length = 60)
  private String wbsAssignmentId;

  /** IC-PMS M8: responsible EPC contractor organisation (FK → admin.organisations). */
  @Column(name = "responsible_contractor_id")
  private UUID responsibleContractorId;

  /** IC-PMS M8: denormalised contractor short-name for register grid display. */
  @Column(name = "responsible_contractor_name", length = 100)
  private String responsibleContractorName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'ACTIVE'")
  @Default
  private ResourceStatus status = ResourceStatus.ACTIVE;

  @Column(name = "hourly_rate", columnDefinition = "double precision default 0.0")
  @Default
  private Double hourlyRate = 0.0;

  @Column(name = "cost_per_use", columnDefinition = "double precision default 0.0")
  @Default
  private Double costPerUse = 0.0;

  @Column(name = "overtime_rate", columnDefinition = "double precision default 0.0")
  @Default
  private Double overtimeRate = 0.0;

  @Column(name = "sort_order", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
  @Default
  private Integer sortOrder = 0;

  // ── PMS MasterData Screen 04 (Equipment Master) fields ───────────────────

  /** Technical specification: bucket size, tonnage, width, etc. E.g. "1.0 Cum", "60 TPH", "5.5 m". */
  @Column(name = "capacity_spec", length = 80)
  private String capacitySpec;

  /** Manufacturer and model number. */
  @Column(name = "make_model", length = 80)
  private String makeModel;

  /** Total number of units of this equipment type available on site. */
  @Column(name = "quantity_available")
  private Integer quantityAvailable;

  /** OWNED / HIRED / SUB_CONTRACTOR_PROVIDED — drives hire-rate applicability. */
  @Enumerated(EnumType.STRING)
  @Column(name = "ownership_type", length = 30)
  private ResourceOwnership ownershipType;

  /** Expected daily production output (e.g. 500 Cum/day for an excavator). From productivity norms. */
  @Column(name = "standard_output_per_day")
  private Double standardOutputPerDay;

  /** Average fuel burn in litres per hour — used in equipment cost computations. */
  @Column(name = "fuel_litres_per_hour", precision = 10, scale = 2)
  private BigDecimal fuelLitresPerHour;

  /** Uncompressed unit of the {@link #standardOutputPerDay} metric (e.g. "Cum", "MT", "Sqm"). */
  @Column(name = "standard_output_unit", length = 20)
  private String standardOutputUnit;
}
