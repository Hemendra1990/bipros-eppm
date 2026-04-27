package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.RiskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for an activity assigned to a risk, including the activity's
 * date range which drives exposure start/finish.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskActivityAssignmentDto {
    private UUID id;
    private UUID riskId;
    private UUID activityId;
    private UUID projectId;

    /** Populated from the activity module when listing. */
    private String activityCode;
    private String activityName;
    private LocalDate activityStartDate;
    private LocalDate activityFinishDate;
}
