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
 * Manpower Rate Master — one row per priceable combination of (Resource Role, Category, Grade).
 * Carries the productivity unit and cost per unit that flow into every Manpower {@code Resource}
 * pointing at this row.
 *
 * <p>Revising a row's {@code rate} or {@code unit} cascades to every linked Resource via
 * {@code RateMasterSyncService}.
 */
@Entity
@Table(
    name = "manpower_rate_masters",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_manpower_rate_master_key",
            columnNames = {"role_id", "category_id", "grade_id"})
    },
    indexes = {
        @Index(name = "idx_manpower_rate_master_role", columnList = "role_id"),
        @Index(name = "idx_manpower_rate_master_grade", columnList = "grade_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManpowerRateMaster extends BaseEntity {

  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(name = "grade_id", nullable = false)
  private UUID gradeId;

  @Column(nullable = false, length = 30)
  private String unit;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal rate;

  @Column(nullable = false)
  @Default
  private Boolean active = true;
}
