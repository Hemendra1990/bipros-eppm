package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.RiskCategory;
import com.bipros.risk.domain.model.RiskImpact;
import com.bipros.risk.domain.model.RiskProbability;
import com.bipros.risk.domain.model.RiskRag;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.model.RiskTrend;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskSummary {
    private UUID id;
    private String code;
    private String title;
    private String description;
    private RiskCategory category;
    private RiskStatus status;
    private RiskProbability probability;
    private RiskImpact impact;
    private Double riskScore;
    private UUID ownerId;
    private LocalDate identifiedDate;
    private LocalDate dueDate;
    private String affectedActivities;
    private BigDecimal costImpact;
    private Integer scheduleImpactDays;
    private int sortOrder;

    // ── IC-PMS M7 fields — surfaced so the Risks tab can render RAG / Trend /
    //    Residual / Cost / Schedule impact and tally OPPORTUNITIES tile. ──

    /** RAG band derived from score (CRIMSON/RED/AMBER/GREEN/OPPORTUNITY). */
    private RiskRag rag;

    /** Exposure trend since last review (IMPROVING / STABLE / DEGRADING). */
    private RiskTrend trend;

    /** True for upside risks (opportunities); flips GREEN → OPPORTUNITY tile. */
    private Boolean isOpportunity;

    /** Residual risk score after mitigations applied. */
    private Double residualRiskScore;

    /**
     * Cost-impact score 1-5 per IC-PMS M7 split-impact model.
     * Note: Risk entity persists this as Integer (1-5 scale), distinct from
     * {@link #costImpact} which is the monetary exposure in BigDecimal.
     */
    private Integer impactCost;

    /** Schedule-impact score 1-5 per IC-PMS M7 split-impact model. */
    private Integer impactSchedule;
}
