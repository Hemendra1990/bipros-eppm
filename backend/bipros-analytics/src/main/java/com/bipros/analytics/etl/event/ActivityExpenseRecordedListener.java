package com.bipros.analytics.etl.event;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.ActivityExpenseRecordedEvent;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityExpenseRecordedListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final ActivityExpenseRepository expenseRepository;
    private final ActivityRepository activityRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivityExpenseRecorded(ActivityExpenseRecordedEvent event) {
        try {
            ActivityExpense expense = expenseRepository.findById(event.expenseId()).orElse(null);
            if (expense == null) {
                log.warn("ActivityExpense not found for event: {}", event);
                return;
            }

            Activity activity = activityRepository.findById(event.activityId()).orElse(null);

            // Categorise cost into labour / material / equipment / expense buckets
            // based on the expenseCategory string heuristic.
            String category = expense.getExpenseCategory() != null ? expense.getExpenseCategory().toUpperCase() : "";
            BigDecimal laborCost = category.contains("LABOUR") || category.contains("MANPOWER") ? expense.getActualCost() : BigDecimal.ZERO;
            BigDecimal materialCost = category.contains("MATERIAL") ? expense.getActualCost() : BigDecimal.ZERO;
            BigDecimal equipmentCost = category.contains("EQUIPMENT") ? expense.getActualCost() : BigDecimal.ZERO;
            BigDecimal expenseCost = (!category.contains("LABOUR") && !category.contains("MANPOWER")
                    && !category.contains("MATERIAL") && !category.contains("EQUIPMENT"))
                    ? expense.getActualCost() : BigDecimal.ZERO;

            LocalDate date = expense.getActualStartDate() != null ? expense.getActualStartDate() : LocalDate.now();

            etl.insertCostDaily(
                    event.projectId(),
                    activity != null ? activity.getWbsNodeId() : null,
                    event.activityId(),
                    date,
                    expense.getCostAccountId(),
                    laborCost, materialCost, equipmentCost, expenseCost,
                    expense.getActualCost(),
                    expense.getBudgetedCost(),
                    null);

            log.debug("ETL processed ActivityExpenseRecordedEvent: project={} expense={}",
                    event.projectId(), event.expenseId());
        } catch (Exception e) {
            log.error("ETL failed for ActivityExpenseRecordedEvent: {}", event, e);
            deadLetter.record("cost.activity_expenses", "fact_cost_daily", event, e);
        }
    }
}
