package com.bipros.analytics.application.service;

import com.bipros.analytics.application.dto.AnalyticsHealthResponse;
import com.bipros.analytics.application.dto.ErrorBucket;
import com.bipros.analytics.application.dto.HourlyMetric;
import com.bipros.analytics.application.dto.TopUserRow;
import com.bipros.analytics.application.dto.WatermarkAge;
import com.bipros.analytics.domain.model.AnalyticsAuditLog;
import com.bipros.analytics.domain.repository.AnalyticsAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Reads observability metrics for the admin /admin/analytics-health page.
 *
 * <p>Pulls from two stores:
 * <ul>
 *   <li><b>ClickHouse</b> ({@code etl_watermarks}) — ETL freshness per source table.</li>
 *   <li><b>Postgres</b> ({@code analytics.analytics_audit_log}) — query volume,
 *       error rate, latency percentiles, top users, error histogram.</li>
 * </ul>
 *
 * <p>The ETL pipeline (1-15 min lag) and the audit log (synchronous OLTP writes)
 * are independent stores; that's why this service queries both directly rather
 * than going through a single facade.
 */
@Service
@ConditionalOnProperty(name = "bipros.analytics.assistant.enabled", havingValue = "true")
@Slf4j
public class AnalyticsHealthService {

    private static final long STALE_WATERMARK_SECONDS = 30 * 60L;     // 30 min
    private static final int TOP_USERS_LIMIT = 10;
    private static final int TOP_ERRORS_LIMIT = 10;
    private static final int MIN_WINDOW_HOURS = 1;
    private static final int MAX_WINDOW_HOURS = 168;                  // 7 days

    private static final String SELECT_WATERMARKS_SQL =
            "SELECT table_name, last_synced_at, last_run_status " +
            "FROM etl_watermarks FINAL ORDER BY table_name";

    private final JdbcTemplate clickhouse;
    private final AnalyticsAuditLogRepository auditRepo;

    public AnalyticsHealthService(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                                  AnalyticsAuditLogRepository auditRepo) {
        this.clickhouse = clickhouse;
        this.auditRepo = auditRepo;
    }

    @Transactional(readOnly = true)
    public AnalyticsHealthResponse health(int windowHours) {
        int wh = clamp(windowHours, MIN_WINDOW_HOURS, MAX_WINDOW_HOURS);
        Instant since = Instant.now().minus(wh, ChronoUnit.HOURS);

        List<WatermarkAge> watermarks = readWatermarks();
        List<HourlyMetric> hourly = readHourly(since);
        List<TopUserRow> topUsers = auditRepo.topUsers(since, PageRequest.of(0, TOP_USERS_LIMIT));
        List<ErrorBucket> errors = auditRepo.errorBreakdown(
                since,
                EnumSet.of(AnalyticsAuditLog.Status.SUCCESS, AnalyticsAuditLog.Status.REFUSED),
                PageRequest.of(0, TOP_ERRORS_LIMIT));

        return new AnalyticsHealthResponse(wh, watermarks, hourly, topUsers, errors);
    }

    private List<WatermarkAge> readWatermarks() {
        try {
            Instant now = Instant.now();
            return clickhouse.query(SELECT_WATERMARKS_SQL, (rs, i) -> {
                String name = rs.getString("table_name");
                Timestamp ts = rs.getTimestamp("last_synced_at");
                Instant lastSynced = ts == null ? null : ts.toInstant();
                long ageSec = lastSynced == null ? Long.MAX_VALUE
                        : Math.max(0L, ChronoUnit.SECONDS.between(lastSynced, now));
                boolean stale = ageSec > STALE_WATERMARK_SECONDS;
                String status = rs.getString("last_run_status");
                return new WatermarkAge(name, lastSynced, ageSec, status, stale);
            });
        } catch (Exception ex) {
            log.warn("ANALYTICS_HEALTH_WATERMARKS_FAILED — returning empty list", ex);
            return List.of();
        }
    }

    private List<HourlyMetric> readHourly(Instant since) {
        List<Object[]> rows = auditRepo.hourlyVolumeAndLatency(since);
        List<HourlyMetric> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Instant bucket = ((Timestamp) r[0]).toInstant();
            Long total = ((Number) r[1]).longValue();
            Long errors = ((Number) r[2]).longValue();
            Double p50 = r[3] == null ? null : ((Number) r[3]).doubleValue();
            Double p95 = r[4] == null ? null : ((Number) r[4]).doubleValue();
            out.add(new HourlyMetric(bucket, total, errors, p50, p95));
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
