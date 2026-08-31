package com.bipros.cost.application.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** One row of the per-WBS EVM table, derived on the same cost basis as CostSummaryDto.ofEvm. */
public record WbsEvmRow(
        String code, String name,
        BigDecimal bac, BigDecimal plannedValue, BigDecimal earnedValue, BigDecimal actualCost,
        BigDecimal scheduleVariance, BigDecimal costVariance,
        BigDecimal schedulePerformanceIndex, BigDecimal costPerformanceIndex,
        BigDecimal estimateAtCompletion, BigDecimal varianceAtCompletion
) {
    private static final RoundingMode HU = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public static WbsEvmRow of(String code, String name,
                               BigDecimal bac, BigDecimal ev, BigDecimal pv, BigDecimal ac) {
        BigDecimal b = bac != null ? bac : ZERO;
        BigDecimal e = ev != null ? ev : ZERO;
        BigDecimal p = pv != null ? pv : ZERO;
        BigDecimal a = ac != null ? ac : ZERO;
        BigDecimal cv = e.subtract(a);
        BigDecimal sv = e.subtract(p);
        BigDecimal cpi = a.signum() > 0 ? e.divide(a, 4, HU) : null;
        BigDecimal spi = p.signum() > 0 ? e.divide(p, 4, HU) : null;
        BigDecimal eac = (cpi != null && cpi.signum() > 0) ? b.divide(cpi, 2, HU) : b;
        BigDecimal vac = b.subtract(eac);
        return new WbsEvmRow(code, name, b, p, e, a, sv, cv, spi, cpi, eac, vac);
    }
}
