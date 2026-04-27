package com.bipros.risk.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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

/**
 * Master data for risk categories. Each row belongs to a {@link RiskCategoryType} (parent)
 * and is tagged with one {@link Industry} so the Risk form can filter to categories that
 * make sense for the project's domain. Categories tagged {@link Industry#GENERIC} are
 * shown for every project regardless of industry.
 *
 * <p>Seeded rows carry {@code systemDefault=true} — services block delete and code
 * mutation on these rows. Admins can add custom categories alongside the seeded set.
 */
@Entity
@Table(
    name = "risk_category_master",
    schema = "risk",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_risk_category_master_code", columnNames = {"code"})
    },
    indexes = {
        @Index(name = "idx_risk_category_master_type", columnList = "type_id"),
        @Index(name = "idx_risk_category_master_industry", columnList = "industry"),
        @Index(name = "idx_risk_category_master_active_sort", columnList = "active, sort_order")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskCategoryMaster extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "type_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_risk_category_master_type"))
    private RiskCategoryType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Industry industry;

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
