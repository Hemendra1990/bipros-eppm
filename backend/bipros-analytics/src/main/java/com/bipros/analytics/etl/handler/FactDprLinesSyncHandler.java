package com.bipros.analytics.etl.handler;

import com.bipros.analytics.etl.support.BaseJpaToClickHouseSyncHandler;
import com.bipros.analytics.etl.support.HandlerSupport;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class FactDprLinesSyncHandler extends BaseJpaToClickHouseSyncHandler<DailyProgressReport> {

    private static final String SQL =
            "INSERT INTO fact_dpr_lines (id, project_id, report_date, supervisor_name, " +
            "chainage_from_m, chainage_to_m, activity_name, wbs_node_id, boq_item_no, unit, " +
            "qty_executed, cumulative_qty, weather_condition, remarks, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final DailyProgressReportRepository repo;

    public FactDprLinesSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                                   DailyProgressReportRepository repo) {
        super(clickhouse);
        this.repo = repo;
    }

    @Override public String tableName() { return "fact_dpr_lines"; }
    @Override protected String insertSql() { return SQL; }

    @Override
    protected Page<DailyProgressReport> fetchPage(Instant since, Pageable pageable) {
        return repo.findByUpdatedAtAfter(since, pageable);
    }

    @Override
    protected Object[] mapRow(DailyProgressReport d) {
        return new Object[]{
                HandlerSupport.uuidOrEmpty(d.getId()),
                HandlerSupport.uuidOrEmpty(d.getProjectId()),
                HandlerSupport.toSqlDate(d.getReportDate()),
                d.getSupervisorName(),
                d.getChainageFromM(),
                d.getChainageToM(),
                d.getActivityName(),
                HandlerSupport.uuidOrNull(d.getWbsNodeId()),
                d.getBoqItemNo(),
                d.getUnit(),
                d.getQtyExecuted(),
                d.getCumulativeQty(),
                d.getWeatherCondition(),
                d.getRemarks(),
                HandlerSupport.toSqlTs(d.getCreatedAt()),
                HandlerSupport.toSqlTs(d.getUpdatedAt())
        };
    }
}
