package com.bipros.analytics.domain.repository;

import com.bipros.analytics.application.dto.ErrorBucket;
import com.bipros.analytics.application.dto.TopUserRow;
import com.bipros.analytics.domain.model.AnalyticsAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Audit-log queries. Phase 4 added four aggregations:
 * <ul>
 *   <li>{@link #aggregateDailyByProvider} drives the per-user Usage tab.</li>
 *   <li>{@link #hourlyVolumeAndLatency} drives the admin health page volume + latency charts.</li>
 *   <li>{@link #topUsers} drives the admin "top users" panel.</li>
 *   <li>{@link #errorBreakdown} drives the admin "top errors" panel.</li>
 * </ul>
 *
 * <p>Native queries are used where Postgres-specific functions (date_trunc, percentile_cont)
 * are needed; the audit log is Postgres-only so portability isn't a concern.
 */
@Repository
public interface AnalyticsAuditLogRepository extends JpaRepository<AnalyticsAuditLog, UUID> {

    /**
     * Per-day, per-provider usage rollup for a single user. Returns rows of
     * {@code (day, provider, tokens_in, tokens_out, cost_micros, query_count)} as
     * {@code Object[]}; the service converts to {@code UsageDailyRow}.
     */
    @Query(value = """
            SELECT date_trunc('day', a.created_at)            AS day,
                   COALESCE(a.llm_provider, 'unknown')        AS provider,
                   COALESCE(SUM(a.tokens_input),  0)::bigint  AS tokens_in,
                   COALESCE(SUM(a.tokens_output), 0)::bigint  AS tokens_out,
                   COALESCE(SUM(a.cost_micros),   0)::bigint  AS cost_micros,
                   COUNT(*)::bigint                           AS query_count
              FROM analytics.analytics_audit_log a
             WHERE a.user_id = :userId
               AND a.created_at >= :from
               AND a.created_at <  :to
          GROUP BY day, provider
          ORDER BY day ASC, provider ASC
            """, nativeQuery = true)
    List<Object[]> aggregateDailyByProvider(@Param("userId") UUID userId,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to);

    /**
     * Hourly volume + p50/p95 latency for the admin health page. Native query
     * because {@code percentile_cont(...) WITHIN GROUP (ORDER BY ...)} is not
     * portable JPQL.
     *
     * <p>Returns {@code (bucket, total, errors, p50_ms, p95_ms)}.
     */
    @Query(value = """
            SELECT date_trunc('hour', a.created_at) AS bucket,
                   COUNT(*)::bigint                 AS total,
                   SUM(CASE WHEN a.status NOT IN ('SUCCESS','REFUSED') THEN 1 ELSE 0 END)::bigint AS errors,
                   percentile_cont(0.5)  WITHIN GROUP (ORDER BY a.latency_ms) AS p50_ms,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY a.latency_ms) AS p95_ms
              FROM analytics.analytics_audit_log a
             WHERE a.created_at >= :since
          GROUP BY bucket
          ORDER BY bucket ASC
            """, nativeQuery = true)
    List<Object[]> hourlyVolumeAndLatency(@Param("since") Instant since);

    /** Top users by query count over the window. Limited via {@link Pageable}. */
    @Query("""
            SELECT new com.bipros.analytics.application.dto.TopUserRow(
                       a.userId, COUNT(a))
              FROM AnalyticsAuditLog a
             WHERE a.createdAt >= :since
          GROUP BY a.userId
          ORDER BY COUNT(a) DESC
            """)
    List<TopUserRow> topUsers(@Param("since") Instant since, Pageable pageable);

    /**
     * Top error patterns over the window. Limited via {@link Pageable}.
     *
     * <p>{@code excludedStatuses} should be {@code Set.of(SUCCESS, REFUSED)} —
     * passed as a parameter so we don't have to embed enum FQNs in JPQL.
     */
    @Query("""
            SELECT new com.bipros.analytics.application.dto.ErrorBucket(
                       a.status, a.errorKind, COUNT(a))
              FROM AnalyticsAuditLog a
             WHERE a.createdAt >= :since
               AND a.status NOT IN :excludedStatuses
          GROUP BY a.status, a.errorKind
          ORDER BY COUNT(a) DESC
            """)
    List<ErrorBucket> errorBreakdown(@Param("since") Instant since,
                                     @Param("excludedStatuses") java.util.Collection<AnalyticsAuditLog.Status> excludedStatuses,
                                     Pageable pageable);
}
