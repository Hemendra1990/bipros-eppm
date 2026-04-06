package com.bipros.evm.application.service;

import com.bipros.evm.domain.entity.EtcMethod;
import com.bipros.evm.domain.entity.EvmCalculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared EVM index and EAC/ETC calculation logic used by both EvmService and EvmRollupService.
 */
final class EvmServiceHelper {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int SCALE = 4;

    private EvmServiceHelper() {}

    static void calculateIndices(EvmCalculation calculation) {
        BigDecimal bac = calculation.getBudgetAtCompletion();
        BigDecimal pv = calculation.getPlannedValue();
        BigDecimal ev = calculation.getEarnedValue();
        BigDecimal ac = calculation.getActualCost();

        // SV = EV - PV
        calculation.setScheduleVariance(ev.subtract(pv));

        // CV = EV - AC
        calculation.setCostVariance(ev.subtract(ac));

        // SPI = EV / PV
        Double spi = pv.compareTo(ZERO) != 0
                ? ev.divide(pv, SCALE, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        calculation.setSchedulePerformanceIndex(spi);

        // CPI = EV / AC
        Double cpi = ac.compareTo(ZERO) != 0
                ? ev.divide(ac, SCALE, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        calculation.setCostPerformanceIndex(cpi);

        // EAC
        BigDecimal eac = calculateEAC(bac, ev, ac, cpi, spi, calculation.getEtcMethod());
        calculation.setEstimateAtCompletion(eac);

        // ETC = EAC - AC
        calculation.setEstimateToComplete(eac.subtract(ac));

        // TCPI = (BAC - EV) / (EAC - AC)
        BigDecimal eacMinusAc = eac.subtract(ac);
        Double tcpi = eacMinusAc.compareTo(ZERO) != 0
                ? bac.subtract(ev).divide(eacMinusAc, SCALE, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        calculation.setToCompletePerformanceIndex(tcpi);

        // VAC = BAC - EAC
        calculation.setVarianceAtCompletion(bac.subtract(eac));

        // Performance % complete = EV / BAC × 100
        if (bac.compareTo(ZERO) != 0) {
            calculation.setPerformancePercentComplete(
                    ev.divide(bac, SCALE, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue());
        } else {
            calculation.setPerformancePercentComplete(0.0);
        }
    }

    static BigDecimal calculateEAC(BigDecimal bac, BigDecimal ev, BigDecimal ac,
                                    Double cpi, Double spi, EtcMethod method) {
        if (bac == null || bac.compareTo(ZERO) == 0) {
            return ZERO;
        }
        if (method == null) {
            method = EtcMethod.CPI_BASED;
        }

        return switch (method) {
            case CPI_BASED -> {
                if (cpi != null && cpi > 0) {
                    yield bac.divide(BigDecimal.valueOf(cpi), SCALE, RoundingMode.HALF_UP);
                }
                yield bac; // fallback: if CPI is 0 or null, EAC = BAC
            }
            case SPI_BASED -> {
                if (spi != null && spi > 0) {
                    BigDecimal remaining = bac.subtract(ev)
                            .divide(BigDecimal.valueOf(spi), SCALE, RoundingMode.HALF_UP);
                    yield ac.add(remaining);
                }
                yield bac;
            }
            case CPI_SPI_COMPOSITE -> {
                if (cpi != null && cpi > 0 && spi != null && spi > 0) {
                    double composite = cpi * spi;
                    BigDecimal remaining = bac.subtract(ev)
                            .divide(BigDecimal.valueOf(composite), SCALE, RoundingMode.HALF_UP);
                    yield ac.add(remaining);
                }
                yield bac;
            }
            case MANUAL, MANAGEMENT_OVERRIDE -> bac; // Return BAC as default; caller can override
        };
    }
}
