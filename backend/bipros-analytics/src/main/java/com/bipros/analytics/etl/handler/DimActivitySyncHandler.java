package com.bipros.analytics.etl.handler;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.analytics.etl.support.BaseJpaToClickHouseSyncHandler;
import com.bipros.analytics.etl.support.HandlerSupport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DimActivitySyncHandler extends BaseJpaToClickHouseSyncHandler<Activity> {

    private static final String SQL =
            "INSERT INTO dim_activity (id, code, name, description, project_id, wbs_node_id, " +
            "activity_type, duration_type, percent_complete_type, status, calendar_id, " +
            "is_critical, sort_order, chainage_from_m, chainage_to_m, assigned_to, " +
            "responsible_user_id, primary_constraint_type, primary_constraint_date, " +
            "secondary_constraint_type, secondary_constraint_date, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final ActivityRepository repo;

    public DimActivitySyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                                  ActivityRepository repo) {
        super(clickhouse);
        this.repo = repo;
    }

    @Override public String tableName() { return "dim_activity"; }
    @Override protected String insertSql() { return SQL; }

    @Override
    protected Page<Activity> fetchPage(Instant since, Pageable pageable) {
        return repo.findByUpdatedAtAfter(since, pageable);
    }

    @Override
    protected Object[] mapRow(Activity a) {
        return new Object[]{
                HandlerSupport.uuidOrEmpty(a.getId()),
                a.getCode(),
                a.getName(),
                a.getDescription(),
                HandlerSupport.uuidOrEmpty(a.getProjectId()),
                HandlerSupport.uuidOrEmpty(a.getWbsNodeId()),
                HandlerSupport.enumName(a.getActivityType()),
                HandlerSupport.enumName(a.getDurationType()),
                HandlerSupport.enumName(a.getPercentCompleteType()),
                HandlerSupport.enumName(a.getStatus()),
                HandlerSupport.uuidOrNull(a.getCalendarId()),
                HandlerSupport.boolToInt(a.getIsCritical()),
                a.getSortOrder(),
                a.getChainageFromM(),
                a.getChainageToM(),
                HandlerSupport.uuidOrNull(a.getAssignedTo()),
                HandlerSupport.uuidOrNull(a.getResponsibleUserId()),
                HandlerSupport.enumName(a.getPrimaryConstraintType()),
                HandlerSupport.toSqlDate(a.getPrimaryConstraintDate()),
                HandlerSupport.enumName(a.getSecondaryConstraintType()),
                HandlerSupport.toSqlDate(a.getSecondaryConstraintDate()),
                HandlerSupport.toSqlTs(a.getCreatedAt()),
                HandlerSupport.toSqlTs(a.getUpdatedAt())
        };
    }
}
