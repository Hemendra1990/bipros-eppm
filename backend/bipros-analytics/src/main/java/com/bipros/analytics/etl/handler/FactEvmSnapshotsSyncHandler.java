package com.bipros.analytics.etl.handler;

import com.bipros.analytics.etl.support.BaseJpaToClickHouseSyncHandler;
import com.bipros.analytics.etl.support.HandlerSupport;
import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.evm.domain.repository.EvmCalculationRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class FactEvmSnapshotsSyncHandler extends BaseJpaToClickHouseSyncHandler<EvmCalculation> {

    private static final String SQL =
            "INSERT INTO fact_evm_snapshots (id, project_id, wbs_node_id, activity_id, " +
            "financial_period_id, data_date, budget_at_completion, planned_value, earned_value, " +
            "actual_cost, schedule_variance, cost_variance, schedule_performance_index, " +
            "cost_performance_index, to_complete_performance_index, estimate_at_completion, " +
            "estimate_to_complete, variance_at_completion, performance_percent_complete, " +
            "evm_technique, etc_method, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final EvmCalculationRepository repo;

    public FactEvmSnapshotsSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                                       EvmCalculationRepository repo) {
        super(clickhouse);
        this.repo = repo;
    }

    @Override public String tableName() { return "fact_evm_snapshots"; }
    @Override protected String insertSql() { return SQL; }

    @Override
    protected Page<EvmCalculation> fetchPage(Instant since, Pageable pageable) {
        return repo.findByUpdatedAtAfter(since, pageable);
    }

    @Override
    protected Object[] mapRow(EvmCalculation e) {
        return new Object[]{
                HandlerSupport.uuidOrEmpty(e.getId()),
                HandlerSupport.uuidOrEmpty(e.getProjectId()),
                HandlerSupport.uuidOrEmpty(e.getWbsNodeId()),
                HandlerSupport.uuidOrEmpty(e.getActivityId()),
                HandlerSupport.uuidOrNull(e.getFinancialPeriodId()),
                HandlerSupport.toSqlDate(e.getDataDate()),
                e.getBudgetAtCompletion(),
                e.getPlannedValue(),
                e.getEarnedValue(),
                e.getActualCost(),
                e.getScheduleVariance(),
                e.getCostVariance(),
                e.getSchedulePerformanceIndex(),
                e.getCostPerformanceIndex(),
                e.getToCompletePerformanceIndex(),
                e.getEstimateAtCompletion(),
                e.getEstimateToComplete(),
                e.getVarianceAtCompletion(),
                e.getPerformancePercentComplete(),
                HandlerSupport.enumName(e.getEvmTechnique()),
                HandlerSupport.enumName(e.getEtcMethod()),
                HandlerSupport.toSqlTs(e.getCreatedAt()),
                HandlerSupport.toSqlTs(e.getUpdatedAt())
        };
    }
}
