package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.MonteCarloMilestoneStat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonteCarloMilestoneStatDto {
    private UUID id;
    private UUID simulationId;
    private UUID activityId;
    private String activityCode;
    private String activityName;
    private LocalDate plannedFinishDate;
    private LocalDate p50FinishDate;
    private LocalDate p80FinishDate;
    private LocalDate p90FinishDate;
    private String cdfJson;

    public static MonteCarloMilestoneStatDto from(MonteCarloMilestoneStat m) {
        return new MonteCarloMilestoneStatDto(
            m.getId(), m.getSimulationId(), m.getActivityId(),
            m.getActivityCode(), m.getActivityName(),
            m.getPlannedFinishDate(),
            m.getP50FinishDate(), m.getP80FinishDate(), m.getP90FinishDate(),
            m.getCdfJson());
    }
}
