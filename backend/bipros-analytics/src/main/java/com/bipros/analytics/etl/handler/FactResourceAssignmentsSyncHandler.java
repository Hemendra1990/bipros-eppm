package com.bipros.analytics.etl.handler;

import com.bipros.analytics.etl.support.BaseJpaToClickHouseSyncHandler;
import com.bipros.analytics.etl.support.HandlerSupport;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class FactResourceAssignmentsSyncHandler extends BaseJpaToClickHouseSyncHandler<ResourceAssignment> {

    private static final String SQL =
            "INSERT INTO fact_resource_assignments (id, activity_id, resource_id, project_id, " +
            "role_id, resource_curve_id, planned_units, actual_units, remaining_units, " +
            "at_completion_units, planned_cost, actual_cost, remaining_cost, at_completion_cost, " +
            "rate_type, planned_start_date, planned_finish_date, actual_start_date, " +
            "actual_finish_date, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final ResourceAssignmentRepository repo;

    public FactResourceAssignmentsSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                                              ResourceAssignmentRepository repo) {
        super(clickhouse);
        this.repo = repo;
    }

    @Override public String tableName() { return "fact_resource_assignments"; }
    @Override protected String insertSql() { return SQL; }

    @Override
    protected Page<ResourceAssignment> fetchPage(Instant since, Pageable pageable) {
        return repo.findByUpdatedAtAfter(since, pageable);
    }

    @Override
    protected Object[] mapRow(ResourceAssignment a) {
        return new Object[]{
                HandlerSupport.uuidOrEmpty(a.getId()),
                HandlerSupport.uuidOrEmpty(a.getActivityId()),
                HandlerSupport.uuidOrEmpty(a.getResourceId()),
                HandlerSupport.uuidOrEmpty(a.getProjectId()),
                HandlerSupport.uuidOrNull(a.getRoleId()),
                HandlerSupport.uuidOrNull(a.getResourceCurveId()),
                a.getPlannedUnits(),
                a.getActualUnits(),
                a.getRemainingUnits(),
                a.getAtCompletionUnits(),
                a.getPlannedCost(),
                a.getActualCost(),
                a.getRemainingCost(),
                a.getAtCompletionCost(),
                a.getRateType(),
                HandlerSupport.toSqlDate(a.getPlannedStartDate()),
                HandlerSupport.toSqlDate(a.getPlannedFinishDate()),
                HandlerSupport.toSqlDate(a.getActualStartDate()),
                HandlerSupport.toSqlDate(a.getActualFinishDate()),
                HandlerSupport.toSqlTs(a.getCreatedAt()),
                HandlerSupport.toSqlTs(a.getUpdatedAt())
        };
    }
}
