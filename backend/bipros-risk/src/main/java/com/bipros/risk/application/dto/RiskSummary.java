package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.RiskCategory;
import com.bipros.risk.domain.model.RiskImpact;
import com.bipros.risk.domain.model.RiskProbability;
import com.bipros.risk.domain.model.RiskStatus;
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
}
