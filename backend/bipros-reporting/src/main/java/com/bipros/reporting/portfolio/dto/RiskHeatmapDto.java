package com.bipros.reporting.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiskHeatmapDto(List<Cell> cells, List<TopRisk> topExposureRisks) {

  public record Cell(int probability, int impact, long count) {}

  public record TopRisk(
      UUID riskId,
      UUID projectId,
      String projectCode,
      String code,
      String title,
      String probability,
      String impact,
      double score,
      String rag) {}
}
