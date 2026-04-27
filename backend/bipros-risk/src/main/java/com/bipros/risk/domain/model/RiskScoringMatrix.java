package com.bipros.risk.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Per-project configurable Probability × Impact scoring matrix.
 * Each cell stores the score returned for a given probability (1-5)
 * and impact (1-5) combination.
 */
@Entity
@Table(
    name = "risk_scoring_matrix",
    schema = "risk",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_risk_scoring_matrix_cell",
        columnNames = {"project_id", "probability_value", "impact_value"}),
    indexes = @Index(name = "idx_risk_scoring_matrix_project", columnList = "project_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RiskScoringMatrix extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "probability_value", nullable = false)
    private Integer probabilityValue;

    @Column(name = "impact_value", nullable = false)
    private Integer impactValue;

    @Column(nullable = false)
    private Integer score;
}
