package com.bipros.analytics.etl.handler;

import com.bipros.analytics.etl.support.BaseJpaToClickHouseSyncHandler;
import com.bipros.analytics.etl.support.HandlerSupport;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.ResourceRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DimResourceSyncHandler extends BaseJpaToClickHouseSyncHandler<Resource> {

    private static final String SQL =
            "INSERT INTO dim_resource (id, code, name, resource_type, resource_category, " +
            "cost_category, unit, parent_id, calendar_id, email, phone, title, max_units_per_day, " +
            "default_units_per_time, pool_max_available, status, hourly_rate, cost_per_use, " +
            "overtime_rate, sort_order, capacity_spec, make_model, quantity_available, " +
            "ownership_type, standard_output_per_day, fuel_litres_per_hour, standard_output_unit, " +
            "responsible_contractor_id, responsible_contractor_name, wbs_assignment_id, " +
            "created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final ResourceRepository repo;

    public DimResourceSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                                  ResourceRepository repo) {
        super(clickhouse);
        this.repo = repo;
    }

    @Override public String tableName() { return "dim_resource"; }
    @Override protected String insertSql() { return SQL; }

    @Override
    protected Page<Resource> fetchPage(Instant since, Pageable pageable) {
        return repo.findByUpdatedAtAfter(since, pageable);
    }

    @Override
    protected Object[] mapRow(Resource r) {
        return new Object[]{
                HandlerSupport.uuidOrEmpty(r.getId()),
                r.getCode(),
                r.getName(),
                HandlerSupport.enumName(r.getResourceType()),
                HandlerSupport.enumName(r.getResourceCategory()),
                HandlerSupport.enumName(r.getCostCategory()),
                HandlerSupport.enumName(r.getUnit()),
                HandlerSupport.uuidOrNull(r.getParentId()),
                HandlerSupport.uuidOrNull(r.getCalendarId()),
                r.getEmail(),
                r.getPhone(),
                r.getTitle(),
                r.getMaxUnitsPerDay(),
                r.getDefaultUnitsPerTime(),
                r.getPoolMaxAvailable(),
                HandlerSupport.enumName(r.getStatus()),
                r.getHourlyRate(),
                r.getCostPerUse(),
                r.getOvertimeRate(),
                r.getSortOrder(),
                r.getCapacitySpec(),
                r.getMakeModel(),
                r.getQuantityAvailable(),
                HandlerSupport.enumName(r.getOwnershipType()),
                r.getStandardOutputPerDay(),
                r.getFuelLitresPerHour(),
                r.getStandardOutputUnit(),
                HandlerSupport.uuidOrNull(r.getResponsibleContractorId()),
                r.getResponsibleContractorName(),
                r.getWbsAssignmentId(),
                HandlerSupport.toSqlTs(r.getCreatedAt()),
                HandlerSupport.toSqlTs(r.getUpdatedAt())
        };
    }
}
