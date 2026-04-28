package com.bipros.analytics.etl.handler;

import com.bipros.analytics.etl.support.BaseJpaToClickHouseSyncHandler;
import com.bipros.analytics.etl.support.HandlerSupport;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class FactActivityExpensesSyncHandler extends BaseJpaToClickHouseSyncHandler<ActivityExpense> {

    private static final String SQL =
            "INSERT INTO fact_activity_expenses (id, activity_id, project_id, cost_account_id, " +
            "name, description, expense_category, budgeted_cost, actual_cost, remaining_cost, " +
            "at_completion_cost, percent_complete, planned_start_date, planned_finish_date, " +
            "actual_start_date, actual_finish_date, currency, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final ActivityExpenseRepository repo;

    public FactActivityExpensesSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                                           ActivityExpenseRepository repo) {
        super(clickhouse);
        this.repo = repo;
    }

    @Override public String tableName() { return "fact_activity_expenses"; }
    @Override protected String insertSql() { return SQL; }

    @Override
    protected Page<ActivityExpense> fetchPage(Instant since, Pageable pageable) {
        return repo.findByUpdatedAtAfter(since, pageable);
    }

    @Override
    protected Object[] mapRow(ActivityExpense e) {
        return new Object[]{
                HandlerSupport.uuidOrEmpty(e.getId()),
                HandlerSupport.uuidOrNull(e.getActivityId()),
                HandlerSupport.uuidOrEmpty(e.getProjectId()),
                HandlerSupport.uuidOrNull(e.getCostAccountId()),
                e.getName(),
                e.getDescription(),
                e.getExpenseCategory(),
                e.getBudgetedCost(),
                e.getActualCost(),
                e.getRemainingCost(),
                e.getAtCompletionCost(),
                e.getPercentComplete(),
                HandlerSupport.toSqlDate(e.getPlannedStartDate()),
                HandlerSupport.toSqlDate(e.getPlannedFinishDate()),
                HandlerSupport.toSqlDate(e.getActualStartDate()),
                HandlerSupport.toSqlDate(e.getActualFinishDate()),
                e.getCurrency(),
                HandlerSupport.toSqlTs(e.getCreatedAt()),
                HandlerSupport.toSqlTs(e.getUpdatedAt())
        };
    }
}
