package com.bipros.reporting.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceUtilizationData(
    String projectName,
    int totalResources,
    double avgUtilization,
    List<ResourceUtilRow> resources) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ResourceUtilRow(
      String code,
      String name,
      String type,
      double plannedHours,
      double actualHours,
      double utilPct) {}
}
