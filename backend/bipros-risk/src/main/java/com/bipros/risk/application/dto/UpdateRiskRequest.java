package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.RiskProbability;
import com.bipros.risk.domain.model.RiskResponseType;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.model.RiskType;
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

/**
 * PATCH-style update: only non-null fields are applied.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateRiskRequest {
    @Size(max = 255)
    private String title;
    private String description;
    private UUID categoryId;
    private RiskType riskType;
    private RiskStatus status;
    private RiskProbability probability;
    @Min(1) @Max(5)
    private Integer impactCost;
    @Min(1) @Max(5)
    private Integer impactSchedule;
    private UUID ownerId;
    private LocalDate identifiedDate;
    private UUID identifiedById;
    private LocalDate dueDate;
    private String affectedActivities;
    private BigDecimal costImpact;
    private Integer scheduleImpactDays;
    private Integer sortOrder;

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
}
