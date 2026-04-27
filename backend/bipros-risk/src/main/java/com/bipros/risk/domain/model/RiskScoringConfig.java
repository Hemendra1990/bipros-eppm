package com.bipros.risk.domain.model;

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

import java.util.UUID;

/**
 * Per-project scoring configuration: which impact-aggregation method is
 * used and whether the matrix is active.
 */
@Entity
@Table(name = "risk_scoring_config", schema = "risk",
    uniqueConstraints = @jakarta.persistence.UniqueConstraint(
        name = "uk_risk_scoring_config_project", columnNames = "project_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RiskScoringConfig extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "scoring_method")
    private ScoringMethod scoringMethod = ScoringMethod.HIGHEST_IMPACT;

    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;

    public enum ScoringMethod {
        HIGHEST_IMPACT,
        AVERAGE_IMPACT,
        AVERAGE_INDIVIDUAL
    }
}
