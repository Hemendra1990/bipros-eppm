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

import java.util.UUID;

@Entity
@Table(name = "monte_carlo_activity_stats", schema = "risk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonteCarloActivityStat extends BaseEntity {

    @Column(nullable = false)
    private UUID simulationId;

    @Column(nullable = false)
    private UUID activityId;

    @Column(length = 20)
    private String activityCode;

    @Column(length = 100)
    private String activityName;

    @Column(nullable = false)
    private Double criticalityIndex;

    @Column private Double durationMean;
    @Column private Double durationStddev;
    @Column private Double durationP10;
    @Column private Double durationP90;
    @Column private Double durationSensitivity;
    @Column private Double costSensitivity;
    @Column private Double cruciality;
}
