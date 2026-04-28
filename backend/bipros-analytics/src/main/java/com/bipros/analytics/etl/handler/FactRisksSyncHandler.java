package com.bipros.analytics.etl.handler;

import com.bipros.analytics.etl.support.BaseJpaToClickHouseSyncHandler;
import com.bipros.analytics.etl.support.HandlerSupport;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskCategoryMaster;
import com.bipros.risk.domain.repository.RiskRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class FactRisksSyncHandler extends BaseJpaToClickHouseSyncHandler<Risk> {

    private static final String SQL =
            "INSERT INTO fact_risks (id, project_id, code, title, description, category_id, " +
            "status, risk_type, probability, impact, impact_cost, impact_schedule, risk_score, " +
            "residual_risk_score, rag, trend, owner_id, identified_by_id, identified_date, " +
            "due_date, cost_impact, schedule_impact_days, exposure_start_date, exposure_finish_date, " +
            "pre_response_exposure_cost, post_response_exposure_cost, response_type, " +
            "post_response_probability, post_response_impact_cost, post_response_impact_schedule, " +
            "post_response_risk_score, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final RiskRepository repo;

    public FactRisksSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                                RiskRepository repo) {
        super(clickhouse);
        this.repo = repo;
    }

    @Override public String tableName() { return "fact_risks"; }
    @Override protected String insertSql() { return SQL; }

    @Override
    protected Page<Risk> fetchPage(Instant since, Pageable pageable) {
        return repo.findByUpdatedAtAfter(since, pageable);
    }

    @Override
    protected Object[] mapRow(Risk r) {
        RiskCategoryMaster cat = r.getCategory();
        return new Object[]{
                HandlerSupport.uuidOrEmpty(r.getId()),
                HandlerSupport.uuidOrEmpty(r.getProjectId()),
                r.getCode(),
                r.getTitle(),
                r.getDescription(),
                cat == null ? null : HandlerSupport.uuidOrNull(cat.getId()),
                HandlerSupport.enumName(r.getStatus()),
                HandlerSupport.enumName(r.getRiskType()),
                HandlerSupport.enumName(r.getProbability()),
                HandlerSupport.enumName(r.getImpact()),
                r.getImpactCost(),
                r.getImpactSchedule(),
                r.getRiskScore(),
                r.getResidualRiskScore(),
                HandlerSupport.enumName(r.getRag()),
                HandlerSupport.enumName(r.getTrend()),
                HandlerSupport.uuidOrNull(r.getOwnerId()),
                HandlerSupport.uuidOrNull(r.getIdentifiedById()),
                HandlerSupport.toSqlDate(r.getIdentifiedDate()),
                HandlerSupport.toSqlDate(r.getDueDate()),
                r.getCostImpact(),
                r.getScheduleImpactDays(),
                HandlerSupport.toSqlDate(r.getExposureStartDate()),
                HandlerSupport.toSqlDate(r.getExposureFinishDate()),
                r.getPreResponseExposureCost(),
                r.getPostResponseExposureCost(),
                HandlerSupport.enumName(r.getResponseType()),
                HandlerSupport.enumName(r.getPostResponseProbability()),
                r.getPostResponseImpactCost(),
                r.getPostResponseImpactSchedule(),
                r.getPostResponseRiskScore(),
                HandlerSupport.toSqlTs(r.getCreatedAt()),
                HandlerSupport.toSqlTs(r.getUpdatedAt())
        };
    }
}
