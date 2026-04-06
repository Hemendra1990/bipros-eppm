package com.bipros.portfolio.application.dto;

import com.bipros.portfolio.domain.algorithm.PortfolioOptimizer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OptimizationResultResponse(
    List<UUID> selectedProjectIds,
    List<UUID> excludedProjectIds,
    double totalScore,
    BigDecimal totalBudget,
    BigDecimal remainingBudget,
    int totalSelected,
    int totalExcluded,
    List<String> messages
) {
  public static OptimizationResultResponse from(PortfolioOptimizer.OptimizationResult result) {
    return new OptimizationResultResponse(
        result.selectedProjectIds(),
        result.excludedProjectIds(),
        result.totalScore(),
        result.totalBudget(),
        result.remainingBudget(),
        result.totalSelected(),
        result.totalExcluded(),
        result.messages()
    );
  }
}
