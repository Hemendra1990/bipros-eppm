package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.RiskImpact;
import com.bipros.risk.domain.model.RiskProbability;
import com.bipros.risk.domain.model.RiskResponseType;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.model.RiskType;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
    @Size(max = 100)
    private String code;

    /** Accepts both {@code title} and {@code name}; callers can pick whichever fits their UI. */
    @JsonAlias({"name"})
    @Size(max = 255)
    private String title;

    private String description;

    /** Preferred FK reference to {@code risk_category_master.id}. */
    private UUID categoryId;

    /** Back-compat alias: clients still posting the old category string. */
    @JsonAlias({"category", "riskCategory"})
    private String legacyCategoryCode;

    /** P6-style risk type: THREAT or OPPORTUNITY. */
    private RiskType riskType;

    private RiskProbability probability;
    private RiskImpact impact;

    /** Pre-response cost impact score 1-5. */
    @Min(1) @Max(5)
    private Integer impactCost;

    /** Pre-response schedule impact score 1-5. */
    @Min(1) @Max(5)
    private Integer impactSchedule;

    /** Lifecycle status. Defaults to IDENTIFIED on create. */
    private RiskStatus status;

    private UUID ownerId;
    private LocalDate identifiedDate;
    private UUID identifiedById;
    private LocalDate dueDate;
    private String affectedActivities;
    private BigDecimal costImpact;
    private Integer scheduleImpactDays;
    private int sortOrder;

    // ── P6 Response strategy ──────────────────────────────────────────────

    private RiskResponseType responseType;
    private String responseDescription;

    // ── P6 Post-response impact ───────────────────────────────────────────

    private RiskProbability postResponseProbability;
    @Min(1) @Max(5)
    private Integer postResponseImpactCost;
    @Min(1) @Max(5)
    private Integer postResponseImpactSchedule;

    // ── P6 Descriptive fields ─────────────────────────────────────────────

    private String cause;
    private String effect;
    private String notes;

    /** Guard against both fields being blank — the service calls this in place of @NotBlank. */
    public String requiredTitle() {
        return (title == null || title.isBlank()) ? null : title;
    }
}
