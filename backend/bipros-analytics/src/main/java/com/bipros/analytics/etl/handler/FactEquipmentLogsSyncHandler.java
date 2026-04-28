package com.bipros.analytics.etl.handler;

import com.bipros.analytics.etl.support.BaseJpaToClickHouseSyncHandler;
import com.bipros.analytics.etl.support.HandlerSupport;
import com.bipros.resource.domain.model.EquipmentLog;
import com.bipros.resource.domain.repository.EquipmentLogRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class FactEquipmentLogsSyncHandler extends BaseJpaToClickHouseSyncHandler<EquipmentLog> {

    private static final String SQL =
            "INSERT INTO fact_equipment_logs (id, resource_id, project_id, log_date, deployment_site, " +
            "operating_hours, idle_hours, breakdown_hours, fuel_consumed, operator_name, status, " +
            "remarks, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final EquipmentLogRepository repo;

    public FactEquipmentLogsSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                                        EquipmentLogRepository repo) {
        super(clickhouse);
        this.repo = repo;
    }

    @Override public String tableName() { return "fact_equipment_logs"; }
    @Override protected String insertSql() { return SQL; }

    @Override
    protected Page<EquipmentLog> fetchPage(Instant since, Pageable pageable) {
        return repo.findByUpdatedAtAfter(since, pageable);
    }

    @Override
    protected Object[] mapRow(EquipmentLog e) {
        return new Object[]{
                HandlerSupport.uuidOrEmpty(e.getId()),
                HandlerSupport.uuidOrEmpty(e.getResourceId()),
                HandlerSupport.uuidOrEmpty(e.getProjectId()),
                HandlerSupport.toSqlDate(e.getLogDate()),
                e.getDeploymentSite(),
                e.getOperatingHours(),
                e.getIdleHours(),
                e.getBreakdownHours(),
                e.getFuelConsumed(),
                e.getOperatorName(),
                HandlerSupport.enumName(e.getStatus()),
                e.getRemarks(),
                HandlerSupport.toSqlTs(e.getCreatedAt()),
                HandlerSupport.toSqlTs(e.getUpdatedAt())
        };
    }
}
