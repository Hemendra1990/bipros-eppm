package com.bipros.reporting.domain.model;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kpi_snapshots", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KpiSnapshot extends BaseEntity {

    @Column(name = "kpi_definition_id", nullable = false)
    private UUID kpiDefinitionId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "period")
    private String period;

    @Column(name = "value")
    private Double value;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private KpiStatus status;

    @Column(name = "calculated_at")
    private Instant calculatedAt;

    public enum KpiStatus {
        GREEN, AMBER, RED
    }
}
