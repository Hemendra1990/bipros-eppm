package com.bipros.reporting.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractStatusData(
    String projectName,
    int totalContracts,
    int activeContracts,
    BigDecimal totalContractValue,
    BigDecimal totalVoValue,
    int pendingMilestones,
    int achievedMilestones,
    List<ContractSummaryRow> contracts) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ContractSummaryRow(
      String contractNumber,
      String contractor,
      BigDecimal value,
      String status,
      int milestonesPending) {}
}
