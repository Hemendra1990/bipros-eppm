package com.bipros.evm.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WbsEvmNode(
        UUID wbsNodeId,
        String name,
        String code,
        BigDecimal budgetAtCompletion,
        BigDecimal plannedValue,
        BigDecimal earnedValue,
        BigDecimal actualCost,
        BigDecimal scheduleVariance,
        BigDecimal costVariance,
        Double schedulePerformanceIndex,
        Double costPerformanceIndex,
        BigDecimal estimateAtCompletion,
        BigDecimal estimateToComplete,
        BigDecimal varianceAtCompletion,
        List<WbsEvmNode> children
) {}
