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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "monte_carlo_cashflow_buckets", schema = "risk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonteCarloCashflowBucket extends BaseEntity {

    @Column(nullable = false)
    private UUID simulationId;

    @Column(nullable = false)
    private LocalDate periodEndDate;

    @Column
    private BigDecimal baselineCumulative;

    @Column
    private BigDecimal p10Cumulative;

    @Column
    private BigDecimal p50Cumulative;

    @Column
    private BigDecimal p80Cumulative;

    @Column
    private BigDecimal p90Cumulative;
}
