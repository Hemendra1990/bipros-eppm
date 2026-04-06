package com.bipros.portfolio.domain.algorithm;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Budget-constrained portfolio optimization using greedy knapsack.
 * Ranks projects by score/cost ratio and selects the best combination
 * that fits within the budget constraint.
 */
public class PortfolioOptimizer {

  public record ProjectCandidate(
      UUID projectId,
      String projectName,
      double score,
      BigDecimal budget,
      boolean mandatory
  ) {}

  public record OptimizationResult(
      List<UUID> selectedProjectIds,
      List<UUID> excludedProjectIds,
      double totalScore,
      BigDecimal totalBudget,
      BigDecimal remainingBudget,
      int totalSelected,
      int totalExcluded,
      List<String> messages
  ) {}

  /**
   * Greedy knapsack: select projects maximizing weighted score within budget.
   * Mandatory projects are always included first.
   */
  public OptimizationResult optimize(List<ProjectCandidate> candidates, BigDecimal budgetLimit) {
    List<String> messages = new ArrayList<>();
    List<UUID> selected = new ArrayList<>();
    List<UUID> excluded = new ArrayList<>();
    BigDecimal usedBudget = BigDecimal.ZERO;
    double totalScore = 0.0;

    // Phase 1: Include mandatory projects
    for (ProjectCandidate c : candidates) {
      if (c.mandatory()) {
        selected.add(c.projectId());
        usedBudget = usedBudget.add(c.budget() != null ? c.budget() : BigDecimal.ZERO);
        totalScore += c.score();
        messages.add("Mandatory: " + c.projectName() + " (score=" + String.format("%.2f", c.score()) + ")");
      }
    }

    if (budgetLimit != null && usedBudget.compareTo(budgetLimit) > 0) {
      messages.add("WARNING: Mandatory projects alone exceed budget by " +
          usedBudget.subtract(budgetLimit).toPlainString());
    }

    // Phase 2: Rank remaining by score/cost ratio (greedy knapsack)
    List<ProjectCandidate> optional = candidates.stream()
        .filter(c -> !c.mandatory())
        .sorted(scoreCostRatioComparator().reversed())
        .toList();

    for (ProjectCandidate c : optional) {
      BigDecimal projectBudget = c.budget() != null ? c.budget() : BigDecimal.ZERO;
      BigDecimal newTotal = usedBudget.add(projectBudget);

      if (budgetLimit == null || newTotal.compareTo(budgetLimit) <= 0) {
        selected.add(c.projectId());
        usedBudget = newTotal;
        totalScore += c.score();
        messages.add("Selected: " + c.projectName() +
            " (score=" + String.format("%.2f", c.score()) +
            ", budget=" + projectBudget.toPlainString() +
            ", ratio=" + String.format("%.4f", scoreCostRatio(c)) + ")");
      } else {
        excluded.add(c.projectId());
        messages.add("Excluded (budget): " + c.projectName() +
            " (would need " + newTotal.toPlainString() + " > " + budgetLimit.toPlainString() + ")");
      }
    }

    BigDecimal remaining = budgetLimit != null ? budgetLimit.subtract(usedBudget) : null;

    return new OptimizationResult(
        selected, excluded, totalScore, usedBudget, remaining,
        selected.size(), excluded.size(), messages
    );
  }

  /**
   * What-if analysis: simulate adding or removing a project from current selection.
   */
  public WhatIfResult whatIf(
      List<ProjectCandidate> currentSelection,
      ProjectCandidate targetProject,
      boolean addProject,
      BigDecimal budgetLimit
  ) {
    double currentScore = currentSelection.stream().mapToDouble(ProjectCandidate::score).sum();
    BigDecimal currentBudget = currentSelection.stream()
        .map(c -> c.budget() != null ? c.budget() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    double newScore;
    BigDecimal newBudget;
    BigDecimal targetBudget = targetProject.budget() != null ? targetProject.budget() : BigDecimal.ZERO;

    if (addProject) {
      newScore = currentScore + targetProject.score();
      newBudget = currentBudget.add(targetBudget);
    } else {
      newScore = currentScore - targetProject.score();
      newBudget = currentBudget.subtract(targetBudget);
    }

    boolean withinBudget = budgetLimit == null || newBudget.compareTo(budgetLimit) <= 0;

    return new WhatIfResult(
        targetProject.projectId(),
        targetProject.projectName(),
        addProject ? "ADD" : "REMOVE",
        currentScore,
        newScore,
        newScore - currentScore,
        currentBudget,
        newBudget,
        newBudget.subtract(currentBudget),
        withinBudget,
        budgetLimit != null ? budgetLimit.subtract(newBudget) : null
    );
  }

  public record WhatIfResult(
      UUID projectId,
      String projectName,
      String action,
      double scoreBefore,
      double scoreAfter,
      double scoreDelta,
      BigDecimal budgetBefore,
      BigDecimal budgetAfter,
      BigDecimal budgetDelta,
      boolean withinBudget,
      BigDecimal remainingBudget
  ) {}

  /**
   * Compare two scenario selections side by side.
   */
  public ScenarioComparison compareScenarios(
      String scenarioAName, List<ProjectCandidate> scenarioA,
      String scenarioBName, List<ProjectCandidate> scenarioB
  ) {
    double scoreA = scenarioA.stream().mapToDouble(ProjectCandidate::score).sum();
    double scoreB = scenarioB.stream().mapToDouble(ProjectCandidate::score).sum();
    BigDecimal budgetA = sumBudget(scenarioA);
    BigDecimal budgetB = sumBudget(scenarioB);

    List<UUID> idsA = scenarioA.stream().map(ProjectCandidate::projectId).toList();
    List<UUID> idsB = scenarioB.stream().map(ProjectCandidate::projectId).toList();

    List<UUID> onlyInA = idsA.stream().filter(id -> !idsB.contains(id)).toList();
    List<UUID> onlyInB = idsB.stream().filter(id -> !idsA.contains(id)).toList();
    List<UUID> common = idsA.stream().filter(idsB::contains).toList();

    return new ScenarioComparison(
        scenarioAName, scenarioBName,
        scoreA, scoreB, scoreB - scoreA,
        budgetA, budgetB, budgetB.subtract(budgetA),
        scenarioA.size(), scenarioB.size(),
        common.size(), onlyInA.size(), onlyInB.size(),
        onlyInA, onlyInB, common
    );
  }

  public record ScenarioComparison(
      String scenarioAName, String scenarioBName,
      double scoreA, double scoreB, double scoreDelta,
      BigDecimal budgetA, BigDecimal budgetB, BigDecimal budgetDelta,
      int projectCountA, int projectCountB,
      int commonProjects, int onlyInACount, int onlyInBCount,
      List<UUID> onlyInA, List<UUID> onlyInB, List<UUID> common
  ) {}

  private static double scoreCostRatio(ProjectCandidate c) {
    BigDecimal budget = c.budget() != null ? c.budget() : BigDecimal.ZERO;
    if (budget.compareTo(BigDecimal.ZERO) == 0) {
      return c.score() > 0 ? Double.MAX_VALUE : 0.0;
    }
    return c.score() / budget.doubleValue();
  }

  private static Comparator<ProjectCandidate> scoreCostRatioComparator() {
    return Comparator.comparingDouble(PortfolioOptimizer::scoreCostRatio);
  }

  private static BigDecimal sumBudget(List<ProjectCandidate> candidates) {
    return candidates.stream()
        .map(c -> c.budget() != null ? c.budget() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
