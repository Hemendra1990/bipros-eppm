package com.bipros.evm.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.evm.application.dto.CalculateEvmRequest;
import com.bipros.evm.application.dto.EvmCalculationResponse;
import com.bipros.evm.application.dto.EvmSummaryResponse;
import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.evm.domain.entity.EtcMethod;
import com.bipros.evm.domain.entity.EvmTechnique;
import com.bipros.evm.domain.repository.EvmCalculationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvmService {

    private final EvmCalculationRepository evmCalculationRepository;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int SCALE = 2;

    @Transactional
    public EvmCalculationResponse calculateEvm(UUID projectId, CalculateEvmRequest request) {
        LocalDate dataDate = LocalDate.now();

        // Create EVM calculation record with initial values
        var calculation = new EvmCalculation();
        calculation.setProjectId(projectId);
        calculation.setDataDate(dataDate);
        calculation.setEvmTechnique(request.technique());
        calculation.setEtcMethod(request.etcMethod());

        // For now, initialize with zeros - in production, would integrate with:
        // - Activity module to get activity costs and percent complete
        // - Resource module to get resource assignments
        // - Baseline module for planned values
        calculation.setBudgetAtCompletion(ZERO);
        calculation.setPlannedValue(ZERO);
        calculation.setEarnedValue(ZERO);
        calculation.setActualCost(ZERO);

        // Calculate indices
        calculateIndices(calculation);

        var saved = evmCalculationRepository.save(calculation);
        return EvmCalculationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public EvmCalculationResponse getLatestEvm(UUID projectId) {
        var entity = evmCalculationRepository.findTopByProjectIdOrderByDataDateDesc(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("EvmCalculation", projectId));
        return EvmCalculationResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<EvmCalculationResponse> getEvmHistory(UUID projectId) {
        return evmCalculationRepository.findByProjectIdOrderByDataDateDesc(projectId)
                .stream()
                .map(EvmCalculationResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EvmCalculationResponse getEvmByWbs(UUID projectId, UUID wbsNodeId) {
        var entity = evmCalculationRepository.findTopByProjectIdAndWbsNodeIdOrderByDataDateDesc(projectId, wbsNodeId)
                .orElseThrow(() -> new ResourceNotFoundException("EvmCalculation", wbsNodeId));
        return EvmCalculationResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public EvmSummaryResponse getSummary(UUID projectId) {
        var calculation = evmCalculationRepository.findTopByProjectIdOrderByDataDateDesc(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("EvmCalculation", projectId));

        return new EvmSummaryResponse(
                calculation.getBudgetAtCompletion(),
                calculation.getPlannedValue(),
                calculation.getEarnedValue(),
                calculation.getActualCost(),
                calculation.getScheduleVariance(),
                calculation.getCostVariance(),
                calculation.getSchedulePerformanceIndex(),
                calculation.getCostPerformanceIndex(),
                calculation.getToCompletePerformanceIndex(),
                calculation.getEstimateAtCompletion(),
                calculation.getEstimateToComplete(),
                calculation.getVarianceAtCompletion(),
                calculation.getEvmTechnique() != null ? calculation.getEvmTechnique().toString() : null,
                calculation.getEtcMethod() != null ? calculation.getEtcMethod().toString() : null,
                calculation.getPerformancePercentComplete()
        );
    }

    private void calculateIndices(EvmCalculation calculation) {
        BigDecimal bac = calculation.getBudgetAtCompletion();
        BigDecimal pv = calculation.getPlannedValue();
        BigDecimal ev = calculation.getEarnedValue();
        BigDecimal ac = calculation.getActualCost();

        // SV = EV - PV
        BigDecimal sv = ev.subtract(pv);
        calculation.setScheduleVariance(sv);

        // CV = EV - AC
        BigDecimal cv = ev.subtract(ac);
        calculation.setCostVariance(cv);

        // SPI = EV / PV (avoid division by zero)
        Double spi = !pv.equals(ZERO) ? ev.divide(pv, SCALE, RoundingMode.HALF_UP).doubleValue() : 0.0;
        calculation.setSchedulePerformanceIndex(spi);

        // CPI = EV / AC (avoid division by zero)
        Double cpi = !ac.equals(ZERO) ? ev.divide(ac, SCALE, RoundingMode.HALF_UP).doubleValue() : 0.0;
        calculation.setCostPerformanceIndex(cpi);

        // Calculate EAC based on method
        BigDecimal eac = calculateEAC(bac, ev, ac, cpi, spi, calculation.getEtcMethod());
        calculation.setEstimateAtCompletion(eac);

        // ETC = EAC - AC
        BigDecimal etc = eac.subtract(ac);
        calculation.setEstimateToComplete(etc);

        // TCPI = (BAC - EV) / (EAC - AC)
        Double tcpi = 0.0;
        BigDecimal eacMinusAc = eac.subtract(ac);
        if (!eacMinusAc.equals(ZERO)) {
            tcpi = bac.subtract(ev).divide(eacMinusAc, SCALE, RoundingMode.HALF_UP).doubleValue();
        }
        calculation.setToCompletePerformanceIndex(tcpi);

        // VAC = BAC - EAC
        BigDecimal vac = bac.subtract(eac);
        calculation.setVarianceAtCompletion(vac);
    }

    private BigDecimal calculateEAC(BigDecimal bac, BigDecimal ev, BigDecimal ac, Double cpi, Double spi, EtcMethod method) {
        if (bac == null || bac.equals(ZERO)) {
            return ZERO;
        }

        return switch (method) {
            case MANUAL -> ZERO;  // Use provided value
            case CPI_BASED -> {
                // EAC = BAC / CPI
                if (cpi != null && cpi > 0) {
                    yield BigDecimal.valueOf(bac.doubleValue() / cpi);
                }
                yield ZERO;
            }
            case SPI_BASED -> {
                // EAC = AC + (BAC - EV) / SPI
                if (spi != null && spi > 0) {
                    BigDecimal remaining = bac.subtract(ev).divide(BigDecimal.valueOf(spi), SCALE, RoundingMode.HALF_UP);
                    yield ac.add(remaining);
                }
                yield ZERO;
            }
            case CPI_SPI_COMPOSITE -> {
                // EAC = AC + (BAC - EV) / (CPI * SPI)
                if (cpi != null && cpi > 0 && spi != null && spi > 0) {
                    double composite = cpi * spi;
                    BigDecimal remaining = bac.subtract(ev).divide(BigDecimal.valueOf(composite), SCALE, RoundingMode.HALF_UP);
                    yield ac.add(remaining);
                }
                yield ZERO;
            }
            case MANAGEMENT_OVERRIDE -> ZERO;  // Use provided value
        };
    }
}
