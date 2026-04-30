package com.bipros.analytics.etl.batch;

import com.bipros.analytics.store.ClickHouseTemplate;
import com.bipros.common.scheduling.ScheduledJobLeaseRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nightly reconciliation: compare Postgres counts + sums vs ClickHouse for the last 7 days.
 * On >0.1% divergence, logs a warning and emits a Micrometer counter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NightlyReconciliationJob {

    private static final String JOB_NAME = "analytics_nightly_reconciliation";
    private static final double DIVERGENCE_THRESHOLD = 0.001;

    private final ScheduledJobLeaseRepository leaseRepository;
    private final ClickHouseTemplate clickHouse;
    private final MeterRegistry meterRegistry;

    @PersistenceContext
    private EntityManager em;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void run() {
        Instant now = Instant.now();
        Instant until = now.plusSeconds(600);
        String owner = "node-" + UUID.randomUUID();
        if (leaseRepository.tryAcquire(JOB_NAME, until, now, owner) == 0) {
            log.debug("NightlyReconciliationJob skipped — another node holds the lease");
            return;
        }

        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();

        long start = System.currentTimeMillis();
        boolean ok = true;

        ok &= reconcile("fact_activity_progress_daily", "project.daily_activity_resource_outputs",
                "SELECT COUNT(*), COALESCE(SUM(qty_executed),0) FROM project.daily_activity_resource_outputs WHERE output_date BETWEEN ?1 AND ?2",
                "SELECT count() as cnt, sum(qty_executed) as sm FROM bipros_analytics.fact_activity_progress_daily WHERE date BETWEEN :from AND :to",
                from, to);

        ok &= reconcile("fact_dpr_logs", "project.daily_progress_reports",
                "SELECT COUNT(*), COALESCE(SUM(qty_executed),0) FROM project.daily_progress_reports WHERE report_date BETWEEN ?1 AND ?2",
                "SELECT count() as cnt, sum(qty_executed) as sm FROM bipros_analytics.fact_dpr_logs WHERE report_date BETWEEN :from AND :to",
                from, to);

        ok &= reconcile("fact_cost_daily", "cost.activity_expenses",
                "SELECT COUNT(*), COALESCE(SUM(actual_cost),0) FROM cost.activity_expenses WHERE actual_start_date BETWEEN ?1 AND ?2",
                "SELECT count() as cnt, sum(total_actual) as sm FROM bipros_analytics.fact_cost_daily WHERE date BETWEEN :from AND :to",
                from, to);

        ok &= reconcile("fact_evm_daily", "evm.evm_calculations",
                "SELECT COUNT(*), COALESCE(SUM(actual_cost),0) FROM evm.evm_calculations WHERE data_date BETWEEN ?1 AND ?2",
                "SELECT count() as cnt, sum(ac) as sm FROM bipros_analytics.fact_evm_daily WHERE date BETWEEN :from AND :to",
                from, to);

        ok &= reconcile("fact_risk_snapshot_daily", "risk.risks",
                "SELECT COUNT(*), 0 FROM risk.risks WHERE updated_at >= (CURRENT_DATE - INTERVAL '7 days')",
                "SELECT count() as cnt, sum(impact_cost) as sm FROM bipros_analytics.fact_risk_snapshot_daily WHERE date BETWEEN :from AND :to",
                from, to);

        Timer.builder("bipros.analytics.reconciliation.duration")
                .register(meterRegistry)
                .record(System.currentTimeMillis() - start, java.util.concurrent.TimeUnit.MILLISECONDS);

        if (ok) {
            log.info("NightlyReconciliationJob completed in {} ms — all facts within threshold", System.currentTimeMillis() - start);
        } else {
            log.warn("NightlyReconciliationJob completed in {} ms — some facts exceeded divergence threshold", System.currentTimeMillis() - start);
        }
    }

    private boolean reconcile(String factTable, String sourceTable,
                               String pgSql, String chSql,
                               LocalDate from, LocalDate to) {
        try {
            Object[] pgRow = (Object[]) em.createNativeQuery(pgSql)
                    .setParameter(1, from)
                    .setParameter(2, to)
                    .getSingleResult();
            long pgCount = ((Number) pgRow[0]).longValue();
            BigDecimal pgSum = toBigDecimal(pgRow[1]);

            List<Map<String, Object>> chRows = clickHouse.queryForList(chSql, Map.of("from", from, "to", to));
            Map<String, Object> chRow = chRows.isEmpty() ? Map.of() : chRows.get(0);
            long chCount = chRow.get("cnt") != null ? ((Number) chRow.get("cnt")).longValue() : 0L;
            BigDecimal chSum = toBigDecimal(chRow.get("sm"));

            double countDiv = pgCount > 0 ? Math.abs(pgCount - chCount) / (double) pgCount : (chCount > 0 ? 1.0 : 0.0);
            double sumDiv = pgSum.compareTo(BigDecimal.ZERO) != 0
                    ? pgSum.subtract(chSum).abs().divide(pgSum, 10, java.math.RoundingMode.HALF_UP).doubleValue()
                    : (chSum.compareTo(BigDecimal.ZERO) != 0 ? 1.0 : 0.0);

            boolean ok = countDiv <= DIVERGENCE_THRESHOLD && sumDiv <= DIVERGENCE_THRESHOLD;

            if (!ok) {
                log.warn("Reconciliation divergence: fact={} source={} date=[{},{}] pgCount={} chCount={} countDiv={} pgSum={} chSum={} sumDiv={}",
                        factTable, sourceTable, from, to, pgCount, chCount, countDiv, pgSum, chSum, sumDiv);
                Counter.builder("bipros.analytics.reconciliation.divergence")
                        .tag("fact", factTable)
                        .register(meterRegistry)
                        .increment();
            }

            return ok;
        } catch (Exception e) {
            log.error("Reconciliation failed for fact={}", factTable, e);
            return false;
        }
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(val.toString());
    }
}
