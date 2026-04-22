package com.bipros.risk.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "monte_carlo_milestone_stats", schema = "risk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonteCarloMilestoneStat extends BaseEntity {

    @Column(nullable = false)
    private UUID simulationId;

    @Column(nullable = false)
    private UUID activityId;

    @Column(length = 20)
    private String activityCode;

    @Column(length = 100)
    private String activityName;

    @Column
    private LocalDate plannedFinishDate;

    @Column
    private LocalDate p50FinishDate;

    @Column
    private LocalDate p80FinishDate;

    @Column
    private LocalDate p90FinishDate;

    /** JSON array of {"date": "yyyy-mm-dd", "p": 0..1} forming a CDF. */
    @Column(columnDefinition = "TEXT")
    private String cdfJson;
}
