package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.RiskCategory;
import com.bipros.risk.domain.model.RiskImpact;
import com.bipros.risk.domain.model.RiskProbability;
import com.bipros.risk.domain.model.RiskStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
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
public class CreateRiskRequest {
    private String code;

    /** Accepts both {@code title} and {@code name}; callers can pick whichever fits their UI. */
    @JsonAlias({"name"})
    private String title;

    private String description;

    /** Accepts both {@code category} and {@code riskCategory}. */
    @JsonAlias({"riskCategory"})
    private RiskCategory category;

    private RiskProbability probability;
    private RiskImpact impact;

    /** IC-PMS M7 split-impact: cost-impact score 1-5. */
    private Integer impactCost;

    /** IC-PMS M7 split-impact: schedule-impact score 1-5. */
    private Integer impactSchedule;

    /** Lifecycle status. Defaults to IDENTIFIED on create; preserved if null on update. */
    private RiskStatus status;

    /** True for opportunities (positive risks). Preserved if null on update. */
    private Boolean isOpportunity;

    private UUID ownerId;
    private LocalDate identifiedDate;
    private LocalDate dueDate;
    private String affectedActivities;
    private BigDecimal costImpact;
    private Integer scheduleImpactDays;
    private int sortOrder;

    /** Guard against both fields being blank — the service calls this in place of @NotBlank. */
    public String requiredTitle() {
        return (title == null || title.isBlank()) ? null : title;
    }
}
