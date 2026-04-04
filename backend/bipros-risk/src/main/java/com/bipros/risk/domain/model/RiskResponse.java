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
@Table(name = "risk_responses", schema = "risk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RiskResponse extends BaseEntity {

    @Column(nullable = false)
    private UUID riskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskResponseType responseType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column
    private UUID responsibleId;

    @Column
    private LocalDate plannedDate;

    @Column
    private LocalDate actualDate;

    @Column
    private BigDecimal estimatedCost;

    @Column
    private BigDecimal actualCost;

    @Column(length = 50)
    private String status = "PLANNED";
}
