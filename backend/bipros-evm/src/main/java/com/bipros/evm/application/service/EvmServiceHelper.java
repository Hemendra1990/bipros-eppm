package com.bipros.evm.application.service;

import com.bipros.evm.domain.entity.EtcMethod;
import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.udf.application.service.FormulaEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * Shared EVM index and EAC/ETC calculation logic used by both EvmService and EvmRollupService.
 * <p>
 * Starting with v2, all core EVM formulas are resolved through the {@link FormulaEngine}
 * so they can be overridden per project. If a formula cannot be resolved or evaluates
 * with an error, the helper falls back to the legacy hard-coded logic to guarantee
 * zero-downtime behaviour.
 */
final class EvmServiceHelper {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int SCALE = 4;

    private EvmServiceHelper() {}

    static void calculateIndices(EvmCalculation calculation, FormulaEngine formulaEngine) {
        UUID projectId = calculation.getProjectId();
        BigDecimal bac = calculation.getBudgetAtCompletion();
        BigDecimal pv = calculation.getPlannedValue();
        BigDecimal ev = calculation.getEarnedValue();
        BigDecimal ac = calculation.getActualCost();

        Map<String, BigDecimal> ctx = Map.of(
                "BAC", nvl(bac),
                "PV", nvl(pv),
                "EV", nvl(ev),
                "AC", nvl(ac)
        );

        // SV = EV - PV
        calculation.setScheduleVariance(
                evalOrFallback(formulaEngine, "EVM_SV", projectId, ctx,
                        () -> ev.subtract(pv)));

        // CV = EV - AC
        calculation.setCostVariance(
                evalOrFallback(formulaEngine, "EVM_CV", projectId, ctx,
                        () -> ev.subtract(ac)));

        // SPI = EV / PV
        Double spi = evalDoubleOrFallback(formulaEngine, "EVM_SPI", projectId, ctx,
                () -> pv.compareTo(ZERO) != 0
                        ? ev.divide(pv, SCALE, RoundingMode.HALF_UP).doubleValue()
                        : 0.0);
        calculation.setSchedulePerformanceIndex(spi);

        // CPI = EV / AC
        Double cpi = evalDoubleOrFallback(formulaEngine, "EVM_CPI", projectId, ctx,
                () -> ac.compareTo(ZERO) != 0
                        ? ev.divide(ac, SCALE, RoundingMode.HALF_UP).doubleValue()
                        : 0.0);
        calculation.setCostPerformanceIndex(cpi);

        // EAC
        BigDecimal eac = calculateEAC(bac, ev, ac, cpi, spi, calculation.getEtcMethod(), formulaEngine, projectId);
        calculation.setEstimateAtCompletion(eac);

        // ETC = EAC - AC
        Map<String, BigDecimal> etcCtx = Map.of(
                "EAC", nvl(eac),
                "AC", nvl(ac)
        );
        calculation.setEstimateToComplete(
                evalOrFallback(formulaEngine, "EVM_ETC", projectId, etcCtx,
                        () -> eac.subtract(ac)));

        // TCPI = (BAC - EV) / (EAC - AC)
        Map<String, BigDecimal> tcpiCtx = Map.of(
                "BAC", nvl(bac),
                "EV", nvl(ev),
                "EAC", nvl(eac),
                "AC", nvl(ac)
        );
        Double tcpi = evalDoubleOrFallback(formulaEngine, "EVM_TCPI", projectId, tcpiCtx,
                () -> {
                    BigDecimal eacMinusAc = eac.subtract(ac);
                    return eacMinusAc.compareTo(ZERO) != 0
                            ? bac.subtract(ev).divide(eacMinusAc, SCALE, RoundingMode.HALF_UP).doubleValue()
                            : 0.0;
                });
        calculation.setToCompletePerformanceIndex(tcpi);

        // VAC = BAC - EAC
        Map<String, BigDecimal> vacCtx = Map.of(
                "BAC", nvl(bac),
                "EAC", nvl(eac)
        );
        calculation.setVarianceAtCompletion(
                evalOrFallback(formulaEngine, "EVM_VAC", projectId, vacCtx,
                        () -> bac.subtract(eac)));

        // Performance % complete = EV / BAC × 100
        Map<String, BigDecimal> perfCtx = Map.of(
                "EV", nvl(ev),
                "BAC", nvl(bac)
        );
        Double perfPct = evalDoubleOrFallback(formulaEngine, "EVM_PERF_PCT", projectId, perfCtx,
                () -> bac.compareTo(ZERO) != 0
                        ? ev.divide(bac, SCALE, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                        : 0.0);
        calculation.setPerformancePercentComplete(perfPct);
    }

    static void calculateIndices(EvmCalculation calculation) {
        // Legacy entry point for callers that don't have FormulaEngine injected.
        // Hard-coded fallback is used for every formula.
        calculateIndices(calculation, null);
    }

    static BigDecimal calculateEAC(BigDecimal bac, BigDecimal ev, BigDecimal ac,
                                    Double cpi, Double spi, EtcMethod method) {
        return calculateEAC(bac, ev, ac, cpi, spi, method, null, null);
    }

    static BigDecimal calculateEAC(BigDecimal bac, BigDecimal ev, BigDecimal ac,
                                    Double cpi, Double spi, EtcMethod method,
                                    FormulaEngine formulaEngine, UUID projectId) {
        if (bac == null || bac.compareTo(ZERO) == 0) {
            return ZERO;
        }
        if (method == null) {
            method = EtcMethod.CPI_BASED;
        }

        Map<String, BigDecimal> ctx = Map.of(
                "BAC", nvl(bac),
                "EV", nvl(ev),
                "AC", nvl(ac),
                "CPI", BigDecimal.valueOf(cpi != null ? cpi : 0.0),
                "SPI", BigDecimal.valueOf(spi != null ? spi : 0.0)
        );

        return switch (method) {
            case CPI_BASED -> {
                if (formulaEngine != null) {
                    BigDecimal result = safeEval(formulaEngine, "EVM_EAC_CPI", projectId, ctx);
                    if (result != null) yield result;
                }
                if (cpi != null && cpi > 0) {
                    yield bac.divide(BigDecimal.valueOf(cpi), SCALE, RoundingMode.HALF_UP);
                }
                yield bac;
            }
            case SPI_BASED -> {
                if (formulaEngine != null) {
                    BigDecimal result = safeEval(formulaEngine, "EVM_EAC_SPI", projectId, ctx);
                    if (result != null) yield result;
                }
                if (spi != null && spi > 0) {
                    BigDecimal remaining = bac.subtract(ev)
                            .divide(BigDecimal.valueOf(spi), SCALE, RoundingMode.HALF_UP);
                    yield ac.add(remaining);
                }
                yield bac;
            }
            case CPI_SPI_COMPOSITE -> {
                if (formulaEngine != null) {
                    BigDecimal result = safeEval(formulaEngine, "EVM_EAC_COMPOSITE", projectId, ctx);
                    if (result != null) yield result;
                }
                if (cpi != null && cpi > 0 && spi != null && spi > 0) {
                    double composite = cpi * spi;
                    BigDecimal remaining = bac.subtract(ev)
                            .divide(BigDecimal.valueOf(composite), SCALE, RoundingMode.HALF_UP);
                    yield ac.add(remaining);
                }
                yield bac;
            }
            case MANUAL, MANAGEMENT_OVERRIDE -> bac;
        };
    }

    // ---- Fallback helpers ----

    private static BigDecimal evalOrFallback(FormulaEngine engine, String code, UUID projectId,
                                              Map<String, BigDecimal> ctx, java.util.function.Supplier<BigDecimal> fallback) {
        BigDecimal result = safeEval(engine, code, projectId, ctx);
        return result != null ? result : fallback.get();
    }

    private static Double evalDoubleOrFallback(FormulaEngine engine, String code, UUID projectId,
                                                Map<String, BigDecimal> ctx, java.util.function.Supplier<Double> fallback) {
        BigDecimal result = safeEval(engine, code, projectId, ctx);
        return result != null ? result.doubleValue() : fallback.get();
    }

    private static BigDecimal safeEval(FormulaEngine engine, String code, UUID projectId,
                                        Map<String, BigDecimal> ctx) {
        if (engine == null) return null;
        try {
            var result = engine.evaluate(code, projectId, ctx);
            if (result.isError()) {
                return null;
            }
            return result.getValue();
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value != null ? value : ZERO;
    }
}
