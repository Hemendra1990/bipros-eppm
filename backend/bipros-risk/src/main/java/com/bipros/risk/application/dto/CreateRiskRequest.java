package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.RiskCategory;
import com.bipros.risk.domain.model.RiskImpact;
import com.bipros.risk.domain.model.RiskProbability;
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
