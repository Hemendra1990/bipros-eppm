package com.bipros.analytics.etl.handler;

import com.bipros.analytics.etl.support.BaseJpaToClickHouseSyncHandler;
import com.bipros.analytics.etl.support.HandlerSupport;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.WbsNodeRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DimWbsSyncHandler extends BaseJpaToClickHouseSyncHandler<WbsNode> {

    private static final String SQL =
            "INSERT INTO dim_wbs (id, code, name, project_id, parent_id, obs_node_id, sort_order, " +
            "wbs_level, wbs_type, phase, wbs_status, asset_class, responsible_organisation_id, " +
            "planned_start, planned_finish, budget_crores, gis_polygon_id, chainage_from_m, " +
            "chainage_to_m, summary_duration, summary_percent_complete, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final WbsNodeRepository repo;

    public DimWbsSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                             WbsNodeRepository repo) {
        super(clickhouse);
        this.repo = repo;
    }

    @Override public String tableName() { return "dim_wbs"; }
    @Override protected String insertSql() { return SQL; }

    @Override
    protected Page<WbsNode> fetchPage(Instant since, Pageable pageable) {
        return repo.findByUpdatedAtAfter(since, pageable);
    }

    @Override
    protected Object[] mapRow(WbsNode w) {
        return new Object[]{
                HandlerSupport.uuidOrEmpty(w.getId()),
                w.getCode(),
                w.getName(),
                HandlerSupport.uuidOrEmpty(w.getProjectId()),
                HandlerSupport.uuidOrNull(w.getParentId()),
                HandlerSupport.uuidOrNull(w.getObsNodeId()),
                w.getSortOrder(),
                w.getWbsLevel(),
                HandlerSupport.enumName(w.getWbsType()),
                HandlerSupport.enumName(w.getPhase()),
                HandlerSupport.enumName(w.getWbsStatus()),
                HandlerSupport.enumName(w.getAssetClass()),
                HandlerSupport.uuidOrNull(w.getResponsibleOrganisationId()),
                HandlerSupport.toSqlDate(w.getPlannedStart()),
                HandlerSupport.toSqlDate(w.getPlannedFinish()),
                w.getBudgetCrores(),
                w.getGisPolygonId(),
                w.getChainageFromM(),
                w.getChainageToM(),
                w.getSummaryDuration(),
                w.getSummaryPercentComplete(),
                HandlerSupport.toSqlTs(w.getCreatedAt()),
                HandlerSupport.toSqlTs(w.getUpdatedAt())
        };
    }
}
