package com.bipros.reporting.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiskRegisterData(
    String projectName,
    int totalRisks,
    int highRisks,
    int mediumRisks,
    int lowRisks,
    Map<String, Integer> risksByCategory,
    List<RiskSummaryRow> topRisks) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record RiskSummaryRow(
      String code,
      String title,
      String category,
      String probability,
      String impact,
      double score) {}
}
