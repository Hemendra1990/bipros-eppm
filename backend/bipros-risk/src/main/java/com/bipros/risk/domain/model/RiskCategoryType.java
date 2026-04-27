package com.bipros.risk.domain.model;

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
 * Parent of {@link RiskCategoryMaster}. Groups categories under a high-level theme
 * (e.g. LAND_ACQUISITION, CROWD_MANAGEMENT, DESIGN_TECHNICAL) so the Risk form can
 * present a Type → Category cascading select.
 *
 * <p>Seeded rows carry {@code systemDefault=true}: services block delete and code mutation
 * on these rows. Admins can add custom types alongside the seeded set.
 */
@Entity
@Table(
    name = "risk_category_type",
    schema = "risk",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_risk_category_type_code", columnNames = {"code"})
    },
    indexes = {
        @Index(name = "idx_risk_category_type_active_sort", columnList = "active, sort_order")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskCategoryType extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Default
    private Boolean active = Boolean.TRUE;

    @Column(name = "sort_order", nullable = false)
    @Default
    private Integer sortOrder = 0;

    @Column(name = "system_default", nullable = false)
    @Default
    private Boolean systemDefault = Boolean.FALSE;
}
