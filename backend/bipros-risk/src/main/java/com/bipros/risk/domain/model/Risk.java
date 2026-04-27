package com.bipros.risk.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private RiskCategoryMaster category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskStatus status = RiskStatus.IDENTIFIED;

    /** P6-style: THREAT or OPPORTUNITY. Replaces legacy isOpportunity boolean. */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_type", length = 20)
    private RiskType riskType = RiskType.THREAT;

    @Enumerated(EnumType.STRING)
    private RiskProbability probability;

    /** Legacy combined impact (kept for back-compat; derived from max of cost/schedule). */
    @Enumerated(EnumType.STRING)
    private RiskImpact impact;

    /** Pre-response cost impact score 1-5. */
    @Column(name = "impact_cost")
    private Integer impactCost;

    /** Pre-response schedule impact score 1-5. */
    @Column(name = "impact_schedule")
    private Integer impactSchedule;

    @Column
    private Double riskScore;

    /** Residual score after mitigations applied. */
    @Column(name = "residual_risk_score")
    private Double residualRiskScore;

    /** RAG band (CRIMSON/RED/AMBER/GREEN/OPPORTUNITY). */
    @Enumerated(EnumType.STRING)
    @Column(name = "rag", length = 20)
    private RiskRag rag;

    /** Exposure trend since last review. */
    @Enumerated(EnumType.STRING)
    @Column(name = "trend", length = 20)
    private RiskTrend trend;

    @Column
    private UUID ownerId;

    @Column
    private LocalDate identifiedDate;

    /** User who identified the risk. */
    @Column(name = "identified_by_id")
    private UUID identifiedById;

    @Column
    private LocalDate dueDate;

    /** Legacy free-text affected activities (kept for back-compat). */
    @Column(columnDefinition = "TEXT")
    private String affectedActivities;

    @Column
    private BigDecimal costImpact;

    @Column
    private Integer scheduleImpactDays;

    @Column
    private int sortOrder;

    // ── P6 Exposure dates (auto-derived from assigned activities) ──────────

    @Column(name = "exposure_start_date")
    private LocalDate exposureStartDate;

    @Column(name = "exposure_finish_date")
    private LocalDate exposureFinishDate;

    @Column(name = "pre_response_exposure_cost", precision = 19, scale = 2)
    private BigDecimal preResponseExposureCost;

    @Column(name = "post_response_exposure_cost", precision = 19, scale = 2)
    private BigDecimal postResponseExposureCost;

    // ── P6 Response strategy (on the risk itself, not the separate RiskResponse) ──

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", length = 20)
    private RiskResponseType responseType;

    @Column(name = "response_description", columnDefinition = "TEXT")
    private String responseDescription;

    // ── P6 Post-response impact (target state after mitigation) ────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "post_response_probability")
    private RiskProbability postResponseProbability;

    @Column(name = "post_response_impact_cost")
    private Integer postResponseImpactCost;

    @Column(name = "post_response_impact_schedule")
    private Integer postResponseImpactSchedule;

    @Column(name = "post_response_risk_score")
    private Double postResponseRiskScore;

    // ── P6 Descriptive fields ─────────────────────────────────────────────

    @Column(columnDefinition = "TEXT")
    private String cause;

    @Column(columnDefinition = "TEXT")
    private String effect;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Is this risk an opportunity? Convenience getter that derives from riskType.
     */
    public boolean isOpportunity() {
        return riskType == RiskType.OPPORTUNITY;
    }

    /**
     * Recompute pre-response riskScore from the scoring matrix.
     * This is the P6-style matrix lookup: Score = f(Probability, DerivedImpact).
     *
     * @param matrixScore the score looked up from the matrix
     */
    public void applyPreResponseScore(Integer matrixScore) {
        if (matrixScore != null && probability != null) {
            this.riskScore = matrixScore.doubleValue();
            this.rag = deriveRag(this.riskScore, isOpportunity());
        }
    }

    /**
     * Recompute post-response riskScore from the scoring matrix.
     *
     * @param matrixScore the score looked up from the matrix
     */
    public void applyPostResponseScore(Integer matrixScore) {
        if (matrixScore != null && postResponseProbability != null) {
            this.postResponseRiskScore = matrixScore.doubleValue();
        }
    }

    /**
     * Derive the impact value based on the scoring method.
     * @param costImpact cost impact score (1-5)
     * @param scheduleImpact schedule impact score (1-5)
     * @param method scoring method
     * @return derived impact value (1-5)
     */
    public static int deriveImpact(Integer costImpact, Integer scheduleImpact,
                                   RiskScoringConfig.ScoringMethod method) {
        int cost = costImpact != null ? costImpact : 0;
        int schedule = scheduleImpact != null ? scheduleImpact : 0;

        return switch (method) {
            case HIGHEST_IMPACT -> Math.max(cost, schedule);
            case AVERAGE_IMPACT -> (cost + schedule) / 2;
            default -> Math.max(cost, schedule);
        };
    }

    /**
     * RAG banding (for opportunities, flip GREEN→OPPORTUNITY):
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
