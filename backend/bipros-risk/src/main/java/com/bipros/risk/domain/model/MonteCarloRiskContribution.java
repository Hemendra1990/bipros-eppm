package com.bipros.risk.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "monte_carlo_risk_contributions", schema = "risk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonteCarloRiskContribution extends BaseEntity {

    @Column(nullable = false)
    private UUID simulationId;

    @Column(nullable = false)
    private UUID riskId;

    @Column(length = 100) private String riskCode;
    @Column(length = 255) private String riskTitle;

    /** Number of iterations in which this risk occurred (Bernoulli = true). */
    @Column private Integer occurrences;

    /** Fraction of iterations the risk occurred (0..1). */
    @Column private Double occurrenceRate;

    /** Mean schedule impact (days) over iterations where the risk occurred. 0 if never occurred. */
    @Column private Double meanDurationImpact;

    /** Mean cost impact (project currency) over iterations where the risk occurred. */
    @Column(precision = 19, scale = 4) private BigDecimal meanCostImpact;

    /** Comma-separated list of activity IDs the risk was wired to. */
    @Column(columnDefinition = "TEXT") private String affectedActivityIds;
}
