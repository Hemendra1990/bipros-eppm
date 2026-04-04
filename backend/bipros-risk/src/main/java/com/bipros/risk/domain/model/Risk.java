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

    @Enumerated(EnumType.STRING)
    private RiskImpact impact;

    @Column
    private Double riskScore;

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

    public void calculateRiskScore() {
        if (probability != null && impact != null) {
            this.riskScore = (double) (probability.getValue() * impact.getValue());
        }
    }
}
