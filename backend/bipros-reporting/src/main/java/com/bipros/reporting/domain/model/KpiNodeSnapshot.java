package com.bipros.reporting.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * IC-PMS M9 per-node KPI snapshot — lets the programme dashboard show 14 KPIs × 5 node
 * columns + programme-rollup column, matching the Excel M9 tile layout.
 */
@Entity
@Table(
    name = "kpi_node_snapshots",
    schema = "public",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_kpi_node_snapshot",
            columnNames = {"kpi_definition_id", "node_code", "period"})
    },
    indexes = {
        @Index(name = "idx_kpi_node_def", columnList = "kpi_definition_id"),
        @Index(name = "idx_kpi_node_code", columnList = "node_code")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiNodeSnapshot extends BaseEntity {

    @Column(name = "kpi_definition_id", nullable = false)
    private UUID kpiDefinitionId;

    /** KPI code (denorm for grid query convenience). */
    @Column(name = "kpi_code", length = 60)
    private String kpiCode;

    /** Node code (e.g. DMIC-N03, DMIC-N04, …, or PROGRAMME for rollup). */
    @Column(name = "node_code", nullable = false, length = 40)
    private String nodeCode;

    @Column(name = "period", length = 20)
    private String period;

    @Column(name = "value")
    private Double value;

    @Column(name = "target_value")
    private Double targetValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "rag", length = 20)
    private KpiSnapshot.KpiStatus rag;

    @Column(name = "calculated_at")
    private Instant calculatedAt;
}
