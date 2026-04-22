package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.MonteCarloActivityStat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonteCarloActivityStatDto {
    private UUID id;
    private UUID simulationId;
    private UUID activityId;
    private String activityCode;
    private String activityName;
    private Double criticalityIndex;
    private Double durationMean;
    private Double durationStddev;
    private Double durationP10;
    private Double durationP90;
    private Double durationSensitivity;
    private Double costSensitivity;
    private Double cruciality;

    public static MonteCarloActivityStatDto from(MonteCarloActivityStat s) {
        return new MonteCarloActivityStatDto(
            s.getId(), s.getSimulationId(), s.getActivityId(),
            s.getActivityCode(), s.getActivityName(),
            s.getCriticalityIndex(),
            s.getDurationMean(), s.getDurationStddev(), s.getDurationP10(), s.getDurationP90(),
            s.getDurationSensitivity(), s.getCostSensitivity(), s.getCruciality());
    }
}
