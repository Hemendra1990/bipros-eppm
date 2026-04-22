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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "monte_carlo_simulations", schema = "risk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonteCarloSimulation extends BaseEntity {

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 255)
    private String simulationName;

    @Column(nullable = false)
    private Integer iterations = 10000;

    @Column private Double p10Duration;
    @Column private Double p25Duration;
    @Column private Double confidenceP50Duration;
    @Column private Double p75Duration;
    @Column private Double confidenceP80Duration;
    @Column private Double p90Duration;
    @Column private Double p95Duration;
    @Column private Double p99Duration;
    @Column private Double meanDuration;
    @Column private Double stddevDuration;

    @Column private BigDecimal p10Cost;
    @Column private BigDecimal p25Cost;
    @Column private BigDecimal confidenceP50Cost;
    @Column private BigDecimal p75Cost;
    @Column private BigDecimal confidenceP80Cost;
    @Column private BigDecimal p90Cost;
    @Column private BigDecimal p95Cost;
    @Column private BigDecimal p99Cost;
    @Column private BigDecimal meanCost;
    @Column private BigDecimal stddevCost;

    @Column(nullable = false)
    private Double baselineDuration;

    @Column(nullable = false)
    private BigDecimal baselineCost;

    @Column
    private UUID baselineId;

    @Column
    private LocalDate dataDate;

    @Column
    private Integer iterationsCompleted;

    @Column(columnDefinition = "TEXT")
    private String configJson;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MonteCarloStatus status = MonteCarloStatus.PENDING;

    public enum MonteCarloStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
