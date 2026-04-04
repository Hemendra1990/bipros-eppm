package com.bipros.scheduling.domain.algorithm;

import com.bipros.scheduling.domain.model.SchedulingOption;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ScheduleData(
    UUID projectId,
    LocalDate dataDate,
    LocalDate projectStartDate,
    LocalDate mustFinishByDate,
    List<SchedulableActivity> activities,
    List<SchedulableRelationship> relationships,
    SchedulingOption schedulingOption
) {
}
