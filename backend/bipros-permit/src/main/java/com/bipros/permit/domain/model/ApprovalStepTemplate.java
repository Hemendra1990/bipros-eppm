package com.bipros.permit.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Approval workflow definition per permit type. CSV in {@code requiredForRiskLevels}
 * (e.g. "HIGH" or "MEDIUM,HIGH") drives whether the step fires for a given permit's risk.
 */
@Entity
@Table(name = "approval_step_template", schema = "permit", uniqueConstraints = {
        @UniqueConstraint(name = "uk_type_step", columnNames = {"permit_type_template_id", "step_no"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalStepTemplate extends BaseEntity {

    @Column(name = "permit_type_template_id", nullable = false)
    private UUID permitTypeTemplateId;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, length = 60)
    private String role;

    /** CSV of {@link RiskLevel} values for which this step is required. Empty = all levels. */
    @Column(name = "required_for_risk_levels", length = 60)
    private String requiredForRiskLevels;

    @Column(name = "is_optional", nullable = false)
    private boolean optional;
}
