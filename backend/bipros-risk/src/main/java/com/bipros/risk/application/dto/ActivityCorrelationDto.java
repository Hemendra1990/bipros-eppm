package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.ActivityCorrelation;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityCorrelationDto {
    private UUID id;
    private UUID projectId;

    @NotNull private UUID activityAId;
    @NotNull private UUID activityBId;

    @NotNull
    @DecimalMin(value = "-0.99", message = "coefficient must be > -1")
    @DecimalMax(value = "0.99", message = "coefficient must be < 1")
    private Double coefficient;

    public static ActivityCorrelationDto from(ActivityCorrelation c) {
        return new ActivityCorrelationDto(
            c.getId(), c.getProjectId(),
            c.getActivityAId(), c.getActivityBId(),
            c.getCoefficient());
    }
}
