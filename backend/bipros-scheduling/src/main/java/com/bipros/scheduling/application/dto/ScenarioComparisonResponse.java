package com.bipros.scheduling.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ScenarioComparisonResponse(
    UUID scenario1Id,
    UUID scenario2Id,
    String scenario1Name,
    String scenario2Name,
    Double duration1,
    Double duration2,
    Double durationDifference,
    BigDecimal cost1,
    BigDecimal cost2,
    BigDecimal costDifference,
    Integer activitiesChanged,
    List<ActivityChangeDetail> changedActivities
) {
  public record ActivityChangeDetail(
      UUID activityId,
      String activityCode,
      Double duration1,
      Double duration2,
      String dateChange1,
      String dateChange2
  ) {}
}
