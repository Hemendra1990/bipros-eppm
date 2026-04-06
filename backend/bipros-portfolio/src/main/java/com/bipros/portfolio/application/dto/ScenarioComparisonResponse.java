package com.bipros.portfolio.application.dto;

import com.bipros.portfolio.domain.algorithm.PortfolioOptimizer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ScenarioComparisonResponse(
    String scenarioAName,
    String scenarioBName,
    double scoreA,
    double scoreB,
    double scoreDelta,
    BigDecimal budgetA,
    BigDecimal budgetB,
    BigDecimal budgetDelta,
    int projectCountA,
    int projectCountB,
    int commonProjects,
    int onlyInACount,
    int onlyInBCount,
    List<UUID> onlyInA,
    List<UUID> onlyInB,
    List<UUID> common
) {
  public static ScenarioComparisonResponse from(PortfolioOptimizer.ScenarioComparison comparison) {
    return new ScenarioComparisonResponse(
        comparison.scenarioAName(),
        comparison.scenarioBName(),
        comparison.scoreA(),
        comparison.scoreB(),
        comparison.scoreDelta(),
        comparison.budgetA(),
        comparison.budgetB(),
        comparison.budgetDelta(),
        comparison.projectCountA(),
        comparison.projectCountB(),
        comparison.commonProjects(),
        comparison.onlyInACount(),
        comparison.onlyInBCount(),
        comparison.onlyInA(),
        comparison.onlyInB(),
        comparison.common()
    );
  }
}
