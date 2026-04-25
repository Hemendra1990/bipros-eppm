package com.bipros.risk.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Admin-managed library of common risks. Each row is a reusable template a Project Manager
 * can copy into their project's risk register via the "Add from Library" flow. Seeded rows
 * ({@code systemDefault=true}) cannot be deleted and their {@code code}/{@code industry}
 * cannot be mutated — admins can still toggle {@code active}, edit title/description/defaults,
 * and add custom rows alongside the seeded set.
 *
 * <p>Mirrors the admin-managed pattern established by {@code ResourceTypeDef} (commit 447b8c6).
 *
 * <p>{@link #applicableProjectCategories} stores the enum names from the project module's
 * {@code ProjectCategory} as plain strings so this module stays decoupled from
 * {@code bipros-project}.
 */
@Entity
@Table(
    name = "risk_templates",
    schema = "risk",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_risk_template_code", columnNames = {"code"})
    },
    indexes = {
        @Index(name = "idx_risk_template_active_sort", columnList = "active, sort_order"),
        @Index(name = "idx_risk_template_industry", columnList = "industry"),
        @Index(name = "idx_risk_template_category", columnList = "category")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskTemplate extends BaseEntity {

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Industry industry;

    /**
     * Project-category tags used to pre-filter the Add-from-Library modal. Stores
     * {@code ProjectCategory} enum names as strings (e.g. "HIGHWAY", "EXPRESSWAY") to
     * avoid a cross-module dependency on {@code bipros-project}. An empty set means the
     * template is industry-wide (applies to any project of the matching industry).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "risk_template_project_categories",
        schema = "risk",
        joinColumns = @JoinColumn(name = "risk_template_id"))
    @Column(name = "project_category", length = 40, nullable = false)
    @Default
    private Set<String> applicableProjectCategories = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private RiskCategory category;

    /** 1-5 default probability copied onto the new Risk when this template is applied. */
    @Column(name = "default_probability")
    private Integer defaultProbability;

    /** 1-5 default cost-impact copied onto the new Risk when this template is applied. */
    @Column(name = "default_impact_cost")
    private Integer defaultImpactCost;

    /** 1-5 default schedule-impact copied onto the new Risk when this template is applied. */
    @Column(name = "default_impact_schedule")
    private Integer defaultImpactSchedule;

    /** Suggested mitigation text. Surfaced in the modal so PMs see how the risk is usually treated. */
    @Column(name = "mitigation_guidance", columnDefinition = "TEXT")
    private String mitigationGuidance;

    @Column(name = "is_opportunity", nullable = false)
    @Default
    private Boolean isOpportunity = Boolean.FALSE;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(nullable = false)
    @Default
    private Boolean active = Boolean.TRUE;

    /**
     * True for seeded library rows. The service blocks delete and rejects mutations to
     * {@code code} / {@code industry} on these rows.
     */
    @Column(name = "system_default", nullable = false)
    @Default
    private Boolean systemDefault = Boolean.FALSE;
}
