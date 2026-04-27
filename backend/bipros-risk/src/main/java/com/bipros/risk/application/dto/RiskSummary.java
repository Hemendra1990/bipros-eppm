package com.bipros.risk.application.dto;

import com.bipros.common.web.json.Views;
import com.bipros.risk.domain.model.RiskImpact;
import com.bipros.risk.domain.model.RiskProbability;
import com.bipros.risk.domain.model.RiskRag;
import com.bipros.risk.domain.model.RiskResponseType;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.model.RiskTrend;
import com.bipros.risk.domain.model.RiskType;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
    /** Embedded category summary with id/code/name/industry plus parent type. Null if uncategorised. */
    private RiskCategorySummaryDto category;
    private RiskStatus status;

    /** P6-style risk type: THREAT or OPPORTUNITY. */
    private RiskType riskType;

    @JsonView(Views.Internal.class) private RiskProbability probability;
    @JsonView(Views.Internal.class) private RiskImpact impact;
    @JsonView(Views.Internal.class) private Double riskScore;
    private UUID ownerId;
    private LocalDate identifiedDate;
    private UUID identifiedById;
    private LocalDate dueDate;
    @JsonView(Views.Internal.class) private String affectedActivities;
    @JsonView(Views.FinanceConfidential.class) private BigDecimal costImpact;
    @JsonView(Views.Internal.class) private Integer scheduleImpactDays;
    private int sortOrder;

    // ── IC-PMS M7 fields ──────────────────────────────────────────────────

    private RiskRag rag;
    private RiskTrend trend;
    private Boolean isOpportunity;
    @JsonView(Views.Internal.class) private Double residualRiskScore;
    @JsonView(Views.Internal.class) private Integer impactCost;
    @JsonView(Views.Internal.class) private Integer impactSchedule;

    /**
     * Computed assessment of how completely this risk has been analysed (owner / rating /
     * description / response). Populated by list/get endpoints; null on the create/update
     * write paths where the caller already has the saved entity.
     */
    private RiskAnalysisQuality analysisQuality;

    // ── P6 Exposure dates (auto-derived from assigned activities) ──────────

    private LocalDate exposureStartDate;
    private LocalDate exposureFinishDate;

    @JsonView(Views.FinanceConfidential.class)
    private BigDecimal preResponseExposureCost;

    @JsonView(Views.FinanceConfidential.class)
    private BigDecimal postResponseExposureCost;

    // ── P6 Response strategy ──────────────────────────────────────────────

    private RiskResponseType responseType;
    private String responseDescription;

    // ── P6 Post-response impact (target state after mitigation) ────────────

    @JsonView(Views.Internal.class) private RiskProbability postResponseProbability;
    @JsonView(Views.Internal.class) private Integer postResponseImpactCost;
    @JsonView(Views.Internal.class) private Integer postResponseImpactSchedule;
    @JsonView(Views.Internal.class) private Double postResponseRiskScore;

    // ── P6 Descriptive fields ─────────────────────────────────────────────

    private String cause;
    private String effect;
    private String notes;

    // ── Assigned activities ───────────────────────────────────────────────

    private List<RiskActivityAssignmentDto> assignedActivities;
}
