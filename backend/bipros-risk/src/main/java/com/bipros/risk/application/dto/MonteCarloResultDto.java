package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.MonteCarloResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonteCarloResultDto {
    private UUID id;
    private UUID simulationId;
    private Integer iterationNumber;
    private Double projectDuration;
    private BigDecimal projectCost;

    public static MonteCarloResultDto from(MonteCarloResult result) {
        return new MonteCarloResultDto(
            result.getId(),
            result.getSimulationId(),
            result.getIterationNumber(),
            result.getProjectDuration(),
            result.getProjectCost()
        );
    }
}
