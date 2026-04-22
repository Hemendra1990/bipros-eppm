package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.MonteCarloCashflowBucket;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonteCarloCashflowBucketDto {
    private UUID id;
    private UUID simulationId;
    private LocalDate periodEndDate;
    private BigDecimal baselineCumulative;
    private BigDecimal p10Cumulative;
    private BigDecimal p50Cumulative;
    private BigDecimal p80Cumulative;
    private BigDecimal p90Cumulative;

    public static MonteCarloCashflowBucketDto from(MonteCarloCashflowBucket b) {
        return new MonteCarloCashflowBucketDto(
            b.getId(), b.getSimulationId(), b.getPeriodEndDate(),
            b.getBaselineCumulative(),
            b.getP10Cumulative(), b.getP50Cumulative(), b.getP80Cumulative(), b.getP90Cumulative());
    }
}
