package com.bipros.cost.application.service;

import com.bipros.cost.application.dto.CostSummaryDto;
import com.bipros.cost.application.dto.PeriodPerformanceRollupDto;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import com.bipros.project.application.service.DprActualCostLookup;
import com.bipros.project.application.service.DprEarnedValueLookup;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PerformanceRollupService {

    private final ProjectRepository projectRepository;
    private final ActivityExpenseRepository activityExpenseRepository;
    private final DprActualCostLookup dprActualCostLookup;
    private final DprEarnedValueLookup dprEarnedValueLookup;
    private final CostService costService;

    /** Per-period (D/W/M) EVM computed live from approved DPRs + BOQ + planned schedule, reconciling
     *  to the Costs/EVM tab totals (one project, one truth). */
    @Transactional(readOnly = true)
    public List<PeriodPerformanceRollupDto> rollup(UUID projectId, String periodType) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return List.of();

        Map<LocalDate, BigDecimal> acByDate = dprActualCostLookup.sumByProjectGroupedByDate(projectId);
        Map<LocalDate, BigDecimal> evByDate = dprEarnedValueLookup.sumByProjectGroupedByDate(projectId);

        Map<LocalDate, BigDecimal> expenseAcByDate = new HashMap<>();
        for (ActivityExpense e : activityExpenseRepository.findByProjectId(projectId)) {
            if (e.getActualStartDate() == null || e.getActualCost() == null) continue;
            expenseAcByDate.merge(e.getActualStartDate(), e.getActualCost(), BigDecimal::add);
        }

        CostSummaryDto summary = costService.getCostSummary(projectId);
        // PV total = BAC × elapsed-schedule-% (the 100M figure). Do NOT use summary.plannedCost().
        BigDecimal pvTotal = summary.plannedValue() == null ? BigDecimal.ZERO : summary.plannedValue();

        return PerformancePeriodAssembler.assemble(
                projectId, periodType,
                project.getPlannedStartDate(), project.getPlannedFinishDate(), LocalDate.now(),
                acByDate, evByDate, expenseAcByDate,
                summary.totalActual(), summary.earnedValue(), pvTotal);
    }
}
