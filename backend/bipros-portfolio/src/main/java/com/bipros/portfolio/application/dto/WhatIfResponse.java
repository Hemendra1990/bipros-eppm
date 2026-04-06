package com.bipros.portfolio.application.dto;

import com.bipros.portfolio.domain.algorithm.PortfolioOptimizer;

import java.math.BigDecimal;
import java.util.UUID;

public record WhatIfResponse(
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
) {
  public static WhatIfResponse from(PortfolioOptimizer.WhatIfResult result) {
    return new WhatIfResponse(
        result.projectId(),
        result.projectName(),
        result.action(),
        result.scoreBefore(),
        result.scoreAfter(),
        result.scoreDelta(),
        result.budgetBefore(),
        result.budgetAfter(),
        result.budgetDelta(),
        result.withinBudget(),
        result.remainingBudget()
    );
  }
}
