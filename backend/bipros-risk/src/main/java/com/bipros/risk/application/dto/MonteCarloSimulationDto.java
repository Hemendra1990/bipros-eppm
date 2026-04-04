package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.MonteCarloSimulation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonteCarloSimulationDto {
    private UUID id;
    private UUID projectId;
    private String simulationName;
    private Integer iterations;
    private Double confidenceP50Duration;
    private Double confidenceP80Duration;
    private BigDecimal confidenceP50Cost;
    private BigDecimal confidenceP80Cost;
    private Double baselineDuration;
    private BigDecimal baselineCost;
    private String status;
    private Instant completedAt;
    private Instant createdAt;
    private List<MonteCarloResultDto> results;

    public static MonteCarloSimulationDto from(MonteCarloSimulation simulation) {
        return new MonteCarloSimulationDto(
            simulation.getId(),
            simulation.getProjectId(),
            simulation.getSimulationName(),
            simulation.getIterations(),
            simulation.getConfidenceP50Duration(),
            simulation.getConfidenceP80Duration(),
            simulation.getConfidenceP50Cost(),
            simulation.getConfidenceP80Cost(),
            simulation.getBaselineDuration(),
            simulation.getBaselineCost(),
            simulation.getStatus().toString(),
            simulation.getCompletedAt(),
            simulation.getCreatedAt(),
            null
        );
    }
}
