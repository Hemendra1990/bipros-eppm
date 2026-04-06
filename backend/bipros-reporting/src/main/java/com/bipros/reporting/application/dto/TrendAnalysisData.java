package com.bipros.reporting.application.dto;

import java.util.List;
import java.util.Map;

public record TrendAnalysisData(
    String projectName,
    List<PeriodMetric> periodMetrics,
    List<MilestoneStatusRow> milestoneStatus,
    Map<String, Integer> activityDistribution,
    List<ResourceLoadingEntry> resourceLoadingTrend
) {

  public record PeriodMetric(
      String period,
      int totalActivities,
      int completedActivities,
      double percentComplete,
      double spiTrend,
      double cpiTrend
  ) {}

  public record MilestoneStatusRow(
      String code,
      String name,
      String plannedDate,
      String actualDate,
      String status,
      int varianceDays
  ) {}

  public record ResourceLoadingEntry(
      String period,
      double plannedHours,
      double actualHours,
      int resourceCount
  ) {}
}
