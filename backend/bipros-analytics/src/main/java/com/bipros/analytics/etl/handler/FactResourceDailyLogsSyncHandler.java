package com.bipros.analytics.etl.handler;

import com.bipros.analytics.etl.support.BaseJpaToClickHouseSyncHandler;
import com.bipros.analytics.etl.support.HandlerSupport;
import com.bipros.resource.domain.model.ResourceDailyLog;
import com.bipros.resource.domain.repository.ResourceDailyLogRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class FactResourceDailyLogsSyncHandler extends BaseJpaToClickHouseSyncHandler<ResourceDailyLog> {

    private static final String SQL =
            "INSERT INTO fact_resource_daily_logs (id, resource_id, log_date, planned_units, " +
            "actual_units, utilisation_percent, wbs_package_code, remarks, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final ResourceDailyLogRepository repo;

    public FactResourceDailyLogsSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                                            ResourceDailyLogRepository repo) {
        super(clickhouse);
        this.repo = repo;
    }

    @Override public String tableName() { return "fact_resource_daily_logs"; }
    @Override protected String insertSql() { return SQL; }

    @Override
    protected Page<ResourceDailyLog> fetchPage(Instant since, Pageable pageable) {
        return repo.findByUpdatedAtAfter(since, pageable);
    }

    @Override
    protected Object[] mapRow(ResourceDailyLog l) {
        return new Object[]{
                HandlerSupport.uuidOrEmpty(l.getId()),
                HandlerSupport.uuidOrEmpty(l.getResourceId()),
                HandlerSupport.toSqlDate(l.getLogDate()),
                l.getPlannedUnits(),
                l.getActualUnits(),
                l.getUtilisationPercent(),
                l.getWbsPackageCode(),
                l.getRemarks(),
                HandlerSupport.toSqlTs(l.getCreatedAt()),
                HandlerSupport.toSqlTs(l.getUpdatedAt())
        };
    }
}
