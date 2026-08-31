package com.bipros.cost.application.service;

import com.bipros.cost.application.dto.MarginActivityDto;
import com.bipros.cost.application.dto.MarginItemDto;
import com.bipros.cost.application.dto.MarginPeriodDto;
import com.bipros.cost.application.dto.MarginSummaryDto;
import com.bipros.project.domain.model.BoqItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * P&L vs Budgeted Unit Rates. Revenue is priced at the project team's internal {@code budgetedRate};
 * all numbers come from {@link MarginRollupService} (one ledger for the whole page, actual cost =
 * {@code CostService.totalActual}). This class only pins the rate basis to {@code budgetedRate}.
 */
@Service
@RequiredArgsConstructor
public class BudgetedMarginService {

    private final MarginRollupService rollup;

    public List<MarginItemDto> marginByBoqItem(UUID projectId) {
        return rollup.items(projectId, BoqItem::getBudgetedRate);
    }

    public List<MarginActivityDto> marginByActivity(UUID projectId) {
        return rollup.activities(projectId, BoqItem::getBudgetedRate);
    }

    public List<MarginPeriodDto> marginByPeriod(UUID projectId, String periodType) {
        return rollup.periods(projectId, periodType, BoqItem::getBudgetedRate);
    }

    public MarginSummaryDto summary(UUID projectId) {
        return rollup.summary(projectId, BoqItem::getBudgetedRate);
    }
}
