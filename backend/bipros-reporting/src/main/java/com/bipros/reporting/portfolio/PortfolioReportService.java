package com.bipros.reporting.portfolio;

import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.evm.domain.repository.EvmCalculationRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.reporting.portfolio.dto.PortfolioEvmRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioReportService {

  private final ProjectRepository projectRepository;
  private final EvmCalculationRepository evmCalculationRepository;

  @Transactional(readOnly = true)
  public List<PortfolioEvmRow> getEvmRollup() {
    List<Project> projects = projectRepository.findAll();
    List<PortfolioEvmRow> rows = new ArrayList<>(projects.size());
    for (Project p : projects) {
      Optional<EvmCalculation> latestOpt =
          evmCalculationRepository.findTopByProjectIdOrderByDataDateDesc(p.getId());

      BigDecimal pv = BigDecimal.ZERO;
      BigDecimal ev = BigDecimal.ZERO;
      BigDecimal ac = BigDecimal.ZERO;
      Double cpi = 0.0;
      Double spi = 0.0;
      BigDecimal cv = BigDecimal.ZERO;
      BigDecimal sv = BigDecimal.ZERO;
      BigDecimal eac = BigDecimal.ZERO;
      BigDecimal bac = BigDecimal.ZERO;

      if (latestOpt.isPresent()) {
        EvmCalculation e = latestOpt.get();
        pv = nullToZero(e.getPlannedValue());
        ev = nullToZero(e.getEarnedValue());
        ac = nullToZero(e.getActualCost());
        cpi = e.getCostPerformanceIndex() != null ? e.getCostPerformanceIndex() : 0.0;
        spi = e.getSchedulePerformanceIndex() != null ? e.getSchedulePerformanceIndex() : 0.0;
        cv = nullToZero(e.getCostVariance());
        sv = nullToZero(e.getScheduleVariance());
        eac = nullToZero(e.getEstimateAtCompletion());
        bac = nullToZero(e.getBudgetAtCompletion());
      }

      rows.add(
          new PortfolioEvmRow(
              p.getId(),
              p.getCode(),
              p.getName(),
              pv,
              ev,
              ac,
              cpi,
              spi,
              cv,
              sv,
              eac,
              bac));
    }
    return rows;
  }

  private static BigDecimal nullToZero(BigDecimal v) {
    return v != null ? v : BigDecimal.ZERO;
  }
}
