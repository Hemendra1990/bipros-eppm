package com.bipros.analytics.etl.handler;

import com.bipros.analytics.etl.support.BaseJpaToClickHouseSyncHandler;
import com.bipros.analytics.etl.support.HandlerSupport;
import com.bipros.resource.domain.model.LabourReturn;
import com.bipros.resource.domain.repository.LabourReturnRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class FactLabourReturnsSyncHandler extends BaseJpaToClickHouseSyncHandler<LabourReturn> {

    private static final String SQL =
            "INSERT INTO fact_labour_returns (id, project_id, contractor_name, return_date, " +
            "skill_category, head_count, man_days, wbs_node_id, site_location, remarks, " +
            "created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final LabourReturnRepository repo;

    public FactLabourReturnsSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                                        LabourReturnRepository repo) {
        super(clickhouse);
        this.repo = repo;
    }

    @Override public String tableName() { return "fact_labour_returns"; }
    @Override protected String insertSql() { return SQL; }

    @Override
    protected Page<LabourReturn> fetchPage(Instant since, Pageable pageable) {
        return repo.findByUpdatedAtAfter(since, pageable);
    }

    @Override
    protected Object[] mapRow(LabourReturn l) {
        return new Object[]{
                HandlerSupport.uuidOrEmpty(l.getId()),
                HandlerSupport.uuidOrEmpty(l.getProjectId()),
                l.getContractorName(),
                HandlerSupport.toSqlDate(l.getReturnDate()),
                HandlerSupport.enumName(l.getSkillCategory()),
                l.getHeadCount(),
                l.getManDays(),
                HandlerSupport.uuidOrNull(l.getWbsNodeId()),
                l.getSiteLocation(),
                l.getRemarks(),
                HandlerSupport.toSqlTs(l.getCreatedAt()),
                HandlerSupport.toSqlTs(l.getUpdatedAt())
        };
    }
}
