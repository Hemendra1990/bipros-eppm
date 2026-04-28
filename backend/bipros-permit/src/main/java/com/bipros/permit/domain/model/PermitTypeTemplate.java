package com.bipros.permit.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "permit_type_template", schema = "permit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermitTypeTemplate extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_risk_level", nullable = false, length = 20)
    private RiskLevel defaultRiskLevel = RiskLevel.MEDIUM;

    @Column(name = "jsa_required", nullable = false)
    private boolean jsaRequired;

    @Column(name = "gas_test_required", nullable = false)
    private boolean gasTestRequired;

    @Column(name = "isolation_required", nullable = false)
    private boolean isolationRequired;

    @Column(name = "blasting_required", nullable = false)
    private boolean blastingRequired;

    @Column(name = "diving_required", nullable = false)
    private boolean divingRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "night_work_policy", nullable = false, length = 20)
    private NightWorkPolicy nightWorkPolicy = NightWorkPolicy.ALLOWED;

    @Column(name = "max_duration_hours", nullable = false)
    private int maxDurationHours;

    @Column(name = "min_approval_role", length = 60)
    private String minApprovalRole;

    @Column(name = "color_hex", length = 9)
    private String colorHex;

    @Column(name = "icon_key", length = 60)
    private String iconKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
