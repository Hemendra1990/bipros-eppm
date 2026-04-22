package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.MonteCarloSimulation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    // Duration percentiles
    private Double p10Duration;
    private Double p25Duration;
    private Double confidenceP50Duration;
    private Double p75Duration;
    private Double confidenceP80Duration;
    private Double p90Duration;
    private Double p95Duration;
    private Double p99Duration;
    private Double meanDuration;
    private Double stddevDuration;

    // Cost percentiles
    private BigDecimal p10Cost;
    private BigDecimal p25Cost;
    private BigDecimal confidenceP50Cost;
    private BigDecimal p75Cost;
    private BigDecimal confidenceP80Cost;
    private BigDecimal p90Cost;
    private BigDecimal p95Cost;
    private BigDecimal p99Cost;
    private BigDecimal meanCost;
    private BigDecimal stddevCost;

    private Double baselineDuration;
    private BigDecimal baselineCost;
    private UUID baselineId;
    private LocalDate dataDate;
    private Integer iterationsCompleted;
    private String configJson;
    private String errorMessage;

    private String status;
    private Instant completedAt;
    private Instant createdAt;
    private List<MonteCarloResultDto> results;

    public static MonteCarloSimulationDto from(MonteCarloSimulation s) {
        MonteCarloSimulationDto d = new MonteCarloSimulationDto();
        d.setId(s.getId());
        d.setProjectId(s.getProjectId());
        d.setSimulationName(s.getSimulationName());
        d.setIterations(s.getIterations());

        d.setP10Duration(s.getP10Duration());
        d.setP25Duration(s.getP25Duration());
        d.setConfidenceP50Duration(s.getConfidenceP50Duration());
        d.setP75Duration(s.getP75Duration());
        d.setConfidenceP80Duration(s.getConfidenceP80Duration());
        d.setP90Duration(s.getP90Duration());
        d.setP95Duration(s.getP95Duration());
        d.setP99Duration(s.getP99Duration());
        d.setMeanDuration(s.getMeanDuration());
        d.setStddevDuration(s.getStddevDuration());

        d.setP10Cost(s.getP10Cost());
        d.setP25Cost(s.getP25Cost());
        d.setConfidenceP50Cost(s.getConfidenceP50Cost());
        d.setP75Cost(s.getP75Cost());
        d.setConfidenceP80Cost(s.getConfidenceP80Cost());
        d.setP90Cost(s.getP90Cost());
        d.setP95Cost(s.getP95Cost());
        d.setP99Cost(s.getP99Cost());
        d.setMeanCost(s.getMeanCost());
        d.setStddevCost(s.getStddevCost());

        d.setBaselineDuration(s.getBaselineDuration());
        d.setBaselineCost(s.getBaselineCost());
        d.setBaselineId(s.getBaselineId());
        d.setDataDate(s.getDataDate());
        d.setIterationsCompleted(s.getIterationsCompleted());
        d.setConfigJson(s.getConfigJson());
        d.setErrorMessage(s.getErrorMessage());

        d.setStatus(s.getStatus() != null ? s.getStatus().toString() : null);
        d.setCompletedAt(s.getCompletedAt());
        d.setCreatedAt(s.getCreatedAt());
        return d;
    }
}
