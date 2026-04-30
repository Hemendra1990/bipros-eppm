package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by {@code RiskService} or {@code MonteCarloService} after a risk is assessed
 * or a Monte Carlo simulation completes. Triggers analytics ETL into {@code fact_risk_snapshot_daily}.
 */
public record RiskAssessedEvent(
    UUID projectId,
    UUID riskId
) {
}
