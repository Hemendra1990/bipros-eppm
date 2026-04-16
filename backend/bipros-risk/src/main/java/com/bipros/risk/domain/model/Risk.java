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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "risks", schema = "risk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Risk extends BaseEntity {

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private RiskCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskStatus status = RiskStatus.IDENTIFIED;

    @Enumerated(EnumType.STRING)
    private RiskProbability probability;

    /** Legacy combined impact (kept for back-compat; derived from max of cost/schedule). */
    @Enumerated(EnumType.STRING)
    private RiskImpact impact;

    /** IC-PMS M7: cost impact score 1-5 (Excel spec splits impact). */
    @Column(name = "impact_cost")
    private Integer impactCost;

    /** IC-PMS M7: schedule impact score 1-5 (Excel spec splits impact). */
    @Column(name = "impact_schedule")
    private Integer impactSchedule;

    @Column
    private Double riskScore;

    /** IC-PMS M7: residual score after mitigations applied. */
    @Column(name = "residual_risk_score")
    private Double residualRiskScore;

    /** IC-PMS M7: RAG band (CRIMSON/RED/AMBER/GREEN/OPPORTUNITY). */
    @Enumerated(EnumType.STRING)
    @Column(name = "rag", length = 20)
    private RiskRag rag;

    /** IC-PMS M7: exposure trend since last review. */
    @Enumerated(EnumType.STRING)
    @Column(name = "trend", length = 20)
    private RiskTrend trend;

    /** IC-PMS M7: flag upside risks (opportunities) so dashboards can style them. */
    @Column(name = "is_opportunity", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isOpportunity = Boolean.FALSE;

    @Column
    private UUID ownerId;

    @Column
    private LocalDate identifiedDate;

    @Column
    private LocalDate dueDate;

    @Column(columnDefinition = "TEXT")
    private String affectedActivities;

    @Column
    private BigDecimal costImpact;

    @Column
    private Integer scheduleImpactDays;

    @Column
    private int sortOrder;

    /**
     * Recompute {@code riskScore = probability * max(impactCost, impactSchedule)}
     * and derive the RAG band per Excel M7 spec.
     */
    public void calculateRiskScore() {
        int impactMax;
        if (impactCost != null || impactSchedule != null) {
            impactMax = Math.max(impactCost != null ? impactCost : 0,
                impactSchedule != null ? impactSchedule : 0);
        } else if (impact != null) {
            impactMax = impact.getValue();
        } else {
            return;
        }
        if (probability != null) {
            this.riskScore = (double) (probability.getValue() * impactMax);
            this.rag = deriveRag(this.riskScore, Boolean.TRUE.equals(this.isOpportunity));
        }
    }

    /**
     * IC-PMS M7 RAG banding (for opportunities, flip GREEN→OPPORTUNITY):
     * <ul>
     *   <li>≥20 → CRIMSON (catastrophic threats only)</li>
     *   <li>≥12 → RED</li>
     *   <li>≥6 → AMBER</li>
     *   <li>&lt;6 → GREEN, or OPPORTUNITY if upside risk</li>
     * </ul>
     */
    public static RiskRag deriveRag(Double score, boolean opportunity) {
        if (score == null) return null;
        if (opportunity) return RiskRag.OPPORTUNITY;
        if (score >= 20) return RiskRag.CRIMSON;
        if (score >= 12) return RiskRag.RED;
        if (score >= 6) return RiskRag.AMBER;
        return RiskRag.GREEN;
    }
}
