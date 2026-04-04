package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.RiskCategory;
import com.bipros.risk.domain.model.RiskImpact;
import com.bipros.risk.domain.model.RiskProbability;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotBlank(message = "Code is required")
    private String code;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;
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
}
