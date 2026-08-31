package com.bipros.ai.agent.support;

import com.bipros.cost.application.dto.CostSummaryDto;
import com.bipros.cost.application.service.CostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Single seam onto the canonical cost/EVM engine ({@link CostService#getCostSummary}) — the exact
 * numbers the Costs/EVM tab renders. Agents read EVM figures from here so they can never drift from
 * the tab, unlike the legacy {@code EvmService} ACTIVITY_PERCENT_COMPLETE path / stored
 * {@code EvmCalculation} rows (a different earned-value basis).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CanonicalEvm {

    private final CostService costService;

    /** Null when the summary can't be built (e.g. empty project) — callers must null-check. */
    public Snapshot of(UUID projectId) {
        try {
            CostSummaryDto d = costService.getCostSummary(projectId);
            if (d == null) return null;
            return new Snapshot(
                    nz(d.bac()), nz(d.totalActual()), nz(d.earnedValue()), nz(d.plannedValue()),
                    d.costPerformanceIndex(), d.schedulePerformanceIndex(),
                    nz(d.costVariance()), nz(d.scheduleVariance()),
                    nz(d.estimateAtCompletion()), nz(d.varianceAtCompletion()),
                    nz(d.costPercentComplete()));
        } catch (Exception ex) {
            log.debug("Canonical EVM unavailable for project {}: {}", projectId, ex.getMessage());
            return null;
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** cpi/spi are nullable (undefined when AC=0 / PV=0) — mirrors the DTO. */
    public record Snapshot(
            BigDecimal bac, BigDecimal ac, BigDecimal ev, BigDecimal pv,
            BigDecimal cpi, BigDecimal spi,
            BigDecimal cv, BigDecimal sv,
            BigDecimal eac, BigDecimal vac,
            BigDecimal costPercentComplete) {

        /** Earned % of budget (0..100) — the canonical Overall-Progress basis. */
        public double earnedPct() {
            return costPercentComplete.doubleValue() * 100.0;
        }

        /** Planned % of budget (0..100). */
        public double plannedPct() {
            return bac.signum() == 0 ? 0.0 : pv.doubleValue() / bac.doubleValue() * 100.0;
        }
    }
}
