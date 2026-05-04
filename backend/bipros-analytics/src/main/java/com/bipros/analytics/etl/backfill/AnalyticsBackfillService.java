package com.bipros.analytics.etl.backfill;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.analytics.etl.batch.EvmDailyInterpolator;
import com.bipros.analytics.etl.event.RiskAssessedListener;
import com.bipros.analytics.store.ClickHouseTemplate;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import com.bipros.project.domain.model.DailyActivityResourceOutput;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.repository.DailyActivityResourceOutputRepository;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.repository.RiskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsBackfillService {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final ClickHouseTemplate clickHouse;

    private final DailyProgressReportRepository dprRepository;
    private final DailyActivityResourceOutputRepository outputRepository;
    private final ActivityExpenseRepository expenseRepository;
    private final RiskRepository riskRepository;
    private final ActivityRepository activityRepository;
    private final ResourceRepository resourceRepository;
    private final EvmDailyInterpolator evmInterpolator;

    @Transactional(readOnly = true)
    public int backfillDpr(LocalDate from, LocalDate to, UUID projectIdOrNull) {
        List<UUID> projectIds = resolveProjectIds(projectIdOrNull);
        int count = 0;
        for (UUID projectId : projectIds) {
            List<DailyProgressReport> dprs = dprRepository
                    .findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(projectId, from, to);
            for (DailyProgressReport dpr : dprs) {
                try {
                    UUID activityId = resolveActivityId(projectId, dpr.getActivityName());
                    etl.insertDprLog(
                            projectId, activityId, dpr.getId(), dpr.getReportDate(),
                            new UUID(0L, 0L),
                            dpr.getSupervisorName(),
                            dpr.getChainageFromM() != null ? dpr.getChainageFromM().doubleValue() : null,
                            dpr.getChainageToM() != null ? dpr.getChainageToM().doubleValue() : null,
                            dpr.getQtyExecuted() != null ? dpr.getQtyExecuted().doubleValue() : null,
                            dpr.getCumulativeQty() != null ? dpr.getCumulativeQty().doubleValue() : null,
                            dpr.getWeatherCondition(),
                            null,
                            sanitizeRemarks(dpr.getRemarks()));

                    etl.insertActivityProgressDaily(
                            projectId, activityId, dpr.getReportDate(),
                            null, null,
                            dpr.getQtyExecuted() != null ? dpr.getQtyExecuted().doubleValue() : null,
                            dpr.getCumulativeQty() != null ? dpr.getCumulativeQty().doubleValue() : null,
                            dpr.getChainageFromM() != null ? dpr.getChainageFromM().doubleValue() : null,
                            dpr.getChainageToM() != null ? dpr.getChainageToM().doubleValue() : null,
                            "dpr");
                    count++;
                } catch (Exception e) {
                    log.warn("Backfill DPR failed: dprId={} error={}", dpr.getId(), e.getMessage());
                    deadLetter.record("project.daily_progress_reports", "fact_dpr_logs", dpr, e);
                }
            }
        }
        log.info("Backfill DPR complete: {} rows processed", count);
        return count;
    }

    @Transactional(readOnly = true)
    public int backfillActivityProgress(LocalDate from, LocalDate to, UUID projectIdOrNull) {
        List<UUID> projectIds = resolveProjectIds(projectIdOrNull);
        int count = 0;
        for (UUID projectId : projectIds) {
            List<DailyActivityResourceOutput> outputs = outputRepository
                    .findByProjectIdAndOutputDateBetweenOrderByOutputDateDescIdAsc(projectId, from, to);
            for (DailyActivityResourceOutput output : outputs) {
                try {
                    Activity activity = activityRepository.findById(output.getActivityId()).orElse(null);
                    Resource resource = resourceRepository.findById(output.getResourceId()).orElse(null);

                    etl.insertActivityProgressDaily(
                            projectId, output.getActivityId(), output.getOutputDate(),
                            activity != null && activity.getPhysicalPercentComplete() != null
                                    ? activity.getPhysicalPercentComplete().floatValue() : null,
                            activity != null && activity.getDurationPercentComplete() != null
                                    ? activity.getDurationPercentComplete().floatValue() : null,
                            output.getQtyExecuted() != null ? output.getQtyExecuted().doubleValue() : null,
                            output.getQtyExecuted() != null ? output.getQtyExecuted().doubleValue() : null,
                            activity != null && activity.getChainageFromM() != null
                                    ? activity.getChainageFromM().doubleValue() : null,
                            activity != null && activity.getChainageToM() != null
                                    ? activity.getChainageToM().doubleValue() : null,
                            "computed");

                    if (resource != null) {
                        Float productivityActual = null;
                        if (output.getHoursWorked() != null && output.getHoursWorked() > 0
                                && output.getQtyExecuted() != null) {
                            productivityActual = output.getQtyExecuted().floatValue() / output.getHoursWorked().floatValue();
                        }
                        etl.insertResourceUsageDaily(
                                projectId, output.getActivityId(), output.getResourceId(),
                                resource.getResourceType() != null ? resource.getResourceType().getCode() : null,
                                output.getOutputDate(),
                                output.getHoursWorked() != null ? output.getHoursWorked().floatValue() : null,
                                output.getDaysWorked() != null ? output.getDaysWorked().floatValue() : null,
                                output.getQtyExecuted() != null ? output.getQtyExecuted().doubleValue() : null,
                                productivityActual, null, null);
                    }
                    count++;
                } catch (Exception e) {
                    log.warn("Backfill activity progress failed: outputId={} error={}", output.getId(), e.getMessage());
                    deadLetter.record("project.daily_activity_resource_outputs", "fact_activity_progress_daily", output, e);
                }
            }
        }
        log.info("Backfill activity progress complete: {} rows processed", count);
        return count;
    }

    @Transactional(readOnly = true)
    public int backfillCost(LocalDate from, LocalDate to, UUID projectIdOrNull) {
        List<UUID> projectIds = resolveProjectIds(projectIdOrNull);
        int count = 0;
        for (UUID projectId : projectIds) {
            List<ActivityExpense> expenses = expenseRepository.findByProjectId(projectId);
            for (ActivityExpense expense : expenses) {
                LocalDate date = expense.getActualStartDate() != null ? expense.getActualStartDate() : LocalDate.now();
                if (date.isBefore(from) || date.isAfter(to)) continue;
                try {
                    Activity activity = activityRepository.findById(expense.getActivityId()).orElse(null);
                    String category = expense.getExpenseCategory() != null ? expense.getExpenseCategory().toUpperCase() : "";
                    BigDecimal laborCost = category.contains("LABOUR") || category.contains("MANPOWER") ? expense.getActualCost() : BigDecimal.ZERO;
                    BigDecimal materialCost = category.contains("MATERIAL") ? expense.getActualCost() : BigDecimal.ZERO;
                    BigDecimal equipmentCost = category.contains("EQUIPMENT") ? expense.getActualCost() : BigDecimal.ZERO;
                    BigDecimal expenseCost = (!category.contains("LABOUR") && !category.contains("MANPOWER")
                            && !category.contains("MATERIAL") && !category.contains("EQUIPMENT"))
                            ? expense.getActualCost() : BigDecimal.ZERO;

                    etl.insertCostDaily(
                            projectId,
                            activity != null ? activity.getWbsNodeId() : null,
                            expense.getActivityId(),
                            date,
                            expense.getCostAccountId(),
                            laborCost, materialCost, equipmentCost, expenseCost,
                            expense.getActualCost(),
                            expense.getBudgetedCost(),
                            null);
                    count++;
                } catch (Exception e) {
                    log.warn("Backfill cost failed: expenseId={} error={}", expense.getId(), e.getMessage());
                    deadLetter.record("cost.activity_expenses", "fact_cost_daily", expense, e);
                }
            }
        }
        log.info("Backfill cost complete: {} rows processed", count);
        return count;
    }

    @Transactional(readOnly = true)
    public int backfillEvm(LocalDate from, LocalDate to, UUID projectIdOrNull) {
        List<UUID> projectIds = resolveProjectIds(projectIdOrNull);
        int count = 0;
        for (UUID projectId : projectIds) {
            try {
                evmInterpolator.interpolateProject(projectId);
                count++;
            } catch (Exception e) {
                log.warn("Backfill EVM failed: projectId={} error={}", projectId, e.getMessage());
            }
        }
        log.info("Backfill EVM complete: {} projects processed", count);
        return count;
    }

    @Transactional(readOnly = true)
    public int backfillRiskSnapshot(LocalDate from, LocalDate to, UUID projectIdOrNull) {
        List<UUID> projectIds = resolveProjectIds(projectIdOrNull);
        int count = 0;
        for (UUID projectId : projectIds) {
            List<Risk> risks = riskRepository.findByProjectId(projectId);
            for (Risk risk : risks) {
                try {
                    etl.insertRiskSnapshotDaily(RiskAssessedListener.toSnapshot(risk, LocalDate.now()));
                    count++;
                } catch (Exception e) {
                    log.warn("Backfill risk failed: riskId={} error={}", risk.getId(), e.getMessage());
                    deadLetter.record("risk.risks", "fact_risk_snapshot_daily", risk, e);
                }
            }
        }
        log.info("Backfill risk snapshot complete: {} rows processed", count);
        return count;
    }

    @Transactional(readOnly = true)
    public BackfillReport backfillAll(LocalDate from, LocalDate to, UUID projectIdOrNull) {
        int dpr = backfillDpr(from, to, projectIdOrNull);
        int activity = backfillActivityProgress(from, to, projectIdOrNull);
        int cost = backfillCost(from, to, projectIdOrNull);
        int evm = backfillEvm(from, to, projectIdOrNull);
        int risk = backfillRiskSnapshot(from, to, projectIdOrNull);
        return new BackfillReport(dpr, activity, cost, evm, risk);
    }

    public long countClickHouseRows(String factTable) {
        List<Map<String, Object>> rows = clickHouse.queryForList(
                "SELECT count() AS cnt FROM bipros_analytics." + factTable, new HashMap<>());
        if (rows.isEmpty()) return 0;
        Object cnt = rows.get(0).get("cnt");
        return cnt instanceof Number ? ((Number) cnt).longValue() : 0;
    }

    private List<UUID> resolveProjectIds(UUID projectIdOrNull) {
        if (projectIdOrNull != null) return List.of(projectIdOrNull);
        return activityRepository.findAll().stream()
                .map(Activity::getProjectId)
                .distinct()
                .toList();
    }

    private UUID resolveActivityId(UUID projectId, String activityName) {
        try {
            List<Activity> activities = activityRepository.findByProjectId(projectId);
            return activities.stream()
                    .filter(a -> a.getName() != null && a.getName().equalsIgnoreCase(activityName))
                    .findFirst()
                    .map(Activity::getId)
                    .orElse(new UUID(0L, 0L));
        } catch (Exception e) {
            return new UUID(0L, 0L);
        }
    }

    private static String sanitizeRemarks(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return "<UNTRUSTED_DATA>" + raw + "</UNTRUSTED_DATA>";
    }

    public record BackfillReport(int dprInserted, int activityInserted, int costInserted,
                                  int evmProjectsProcessed, int riskInserted) {
    }
}
