package com.bipros.analytics.etl.handler;

import com.bipros.analytics.etl.support.BaseJpaToClickHouseSyncHandler;
import com.bipros.analytics.etl.support.HandlerSupport;
import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.repository.ContractRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component
public class FactContractsSyncHandler extends BaseJpaToClickHouseSyncHandler<Contract> {

    private static final String SQL =
            "INSERT INTO fact_contracts (id, project_id, tender_id, contract_number, loa_number, " +
            "contractor_name, contractor_code, contract_value, revised_value, loa_date, start_date, " +
            "completion_date, revised_completion_date, ntp_date, actual_completion_date, dlp_months, " +
            "ld_rate, status, contract_type, description, currency, mobilisation_advance_pct, " +
            "retention_pct, performance_bg_pct, payment_terms_days, billing_cycle, wbs_package_code, " +
            "package_description, spi, cpi, physical_progress_ai, cumulative_ra_bills_crores, " +
            "vo_numbers_issued, vo_value_crores, performance_score, bg_expiry, kpi_refreshed_at, " +
            "created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
            "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final ContractRepository repo;

    public FactContractsSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                                    ContractRepository repo) {
        super(clickhouse);
        this.repo = repo;
    }

    @Override public String tableName() { return "fact_contracts"; }
    @Override protected String insertSql() { return SQL; }

    @Override
    protected Page<Contract> fetchPage(Instant since, Pageable pageable) {
        return repo.findByUpdatedAtAfter(since, pageable);
    }

    @Override
    protected Object[] mapRow(Contract c) {
        return new Object[]{
                HandlerSupport.uuidOrEmpty(c.getId()),
                HandlerSupport.uuidOrEmpty(c.getProjectId()),
                HandlerSupport.uuidOrNull(c.getTenderId()),
                c.getContractNumber(),
                c.getLoaNumber(),
                c.getContractorName(),
                c.getContractorCode(),
                c.getContractValue(),
                c.getRevisedValue(),
                HandlerSupport.toSqlDate(c.getLoaDate()),
                HandlerSupport.toSqlDate(c.getStartDate()),
                HandlerSupport.toSqlDate(c.getCompletionDate()),
                HandlerSupport.toSqlDate(c.getRevisedCompletionDate()),
                HandlerSupport.toSqlDate(c.getNtpDate()),
                HandlerSupport.toSqlDate(c.getActualCompletionDate()),
                c.getDlpMonths(),
                c.getLdRate(),
                HandlerSupport.enumName(c.getStatus()),
                HandlerSupport.enumName(c.getContractType()),
                c.getDescription(),
                c.getCurrency(),
                c.getMobilisationAdvancePct(),
                c.getRetentionPct(),
                c.getPerformanceBgPct(),
                c.getPaymentTermsDays(),
                HandlerSupport.enumName(c.getBillingCycle()),
                c.getWbsPackageCode(),
                c.getPackageDescription(),
                c.getSpi(),
                c.getCpi(),
                c.getPhysicalProgressAi(),
                c.getCumulativeRaBillsCrores(),
                c.getVoNumbersIssued(),
                c.getVoValueCrores(),
                c.getPerformanceScore(),
                HandlerSupport.toSqlDate(c.getBgExpiry()),
                c.getKpiRefreshedAt() == null ? null : Timestamp.from(c.getKpiRefreshedAt().toInstant()),
                HandlerSupport.toSqlTs(c.getCreatedAt()),
                HandlerSupport.toSqlTs(c.getUpdatedAt())
        };
    }
}
