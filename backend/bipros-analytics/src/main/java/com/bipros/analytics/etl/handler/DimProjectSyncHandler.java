package com.bipros.analytics.etl.handler;

import com.bipros.analytics.etl.support.BaseJpaToClickHouseSyncHandler;
import com.bipros.analytics.etl.support.HandlerSupport;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DimProjectSyncHandler extends BaseJpaToClickHouseSyncHandler<Project> {

    private static final String SQL =
            "INSERT INTO dim_project (id, code, name, description, eps_node_id, obs_node_id, " +
            "status, planned_start_date, planned_finish_date, data_date, must_finish_by_date, " +
            "priority, category, morth_code, from_chainage_m, to_chainage_m, from_location, " +
            "to_location, total_length_km, active_baseline_id, owner_id, archived_at, archived_by, " +
            "created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final ProjectRepository repo;

    public DimProjectSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                                 ProjectRepository repo) {
        super(clickhouse);
        this.repo = repo;
    }

    @Override public String tableName() { return "dim_project"; }
    @Override protected String insertSql() { return SQL; }

    @Override
    protected Page<Project> fetchPage(Instant since, Pageable pageable) {
        return repo.findByUpdatedAtAfter(since, pageable);
    }

    @Override
    protected Object[] mapRow(Project p) {
        return new Object[]{
                HandlerSupport.uuidOrEmpty(p.getId()),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                HandlerSupport.uuidOrNull(p.getEpsNodeId()),
                HandlerSupport.uuidOrNull(p.getObsNodeId()),
                HandlerSupport.enumName(p.getStatus()),
                HandlerSupport.toSqlDate(p.getPlannedStartDate()),
                HandlerSupport.toSqlDate(p.getPlannedFinishDate()),
                HandlerSupport.toSqlDate(p.getDataDate()),
                HandlerSupport.toSqlDate(p.getMustFinishByDate()),
                p.getPriority(),
                p.getCategory(),
                p.getMorthCode(),
                p.getFromChainageM(),
                p.getToChainageM(),
                p.getFromLocation(),
                p.getToLocation(),
                p.getTotalLengthKm(),
                HandlerSupport.uuidOrNull(p.getActiveBaselineId()),
                HandlerSupport.uuidOrNull(p.getOwnerId()),
                HandlerSupport.toSqlTs(p.getArchivedAt()),
                HandlerSupport.uuidOrNull(p.getArchivedBy()),
                HandlerSupport.toSqlTs(p.getCreatedAt()),
                HandlerSupport.toSqlTs(p.getUpdatedAt())
        };
    }
}
