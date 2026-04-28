package com.bipros.analytics.etl.handler;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.analytics.etl.SyncHandler;
import com.bipros.analytics.etl.SyncReport;
import com.bipros.analytics.etl.support.HandlerSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Daily snapshot of {@link Activity}. Postgres source is mutable in-place — no temporal
 * column to chase — so each day at the orchestrator's first tick after 24h has elapsed since
 * the last run we capture the full current state into {@code fact_activity_progress} keyed by
 * {@code (activity_id, snapshot_date)}.
 *
 * <p>Re-running the same calendar day is idempotent: {@code ReplacingMergeTree(updated_at)}
 * collapses the rows to the latest write.
 */
@Component
@Slf4j
public class FactActivityProgressSyncHandler implements SyncHandler {

    private static final String SQL =
            "INSERT INTO fact_activity_progress (activity_id, snapshot_date, project_id, " +
            "wbs_node_id, status, percent_complete, physical_percent_complete, " +
            "duration_percent_complete, units_percent_complete, planned_start_date, " +
            "planned_finish_date, early_start_date, early_finish_date, late_start_date, " +
            "late_finish_date, actual_start_date, actual_finish_date, original_duration, " +
            "remaining_duration, at_completion_duration, total_float, free_float, is_critical, " +
            "suspend_date, resume_date, assigned_to, responsible_user_id, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final int PAGE_SIZE = 5_000;

    private final JdbcTemplate ch;
    private final ActivityRepository activities;

    public FactActivityProgressSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate ch,
                                           ActivityRepository activities) {
        this.ch = ch;
        this.activities = activities;
    }

    @Override public String tableName() { return "fact_activity_progress"; }
    @Override public Duration cadence() { return Duration.ofDays(1); }

    @Override
    @Transactional(readOnly = true)
    public SyncReport sync(Instant since) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Date snapshotDate = Date.valueOf(today);
        Instant runAt = Instant.now();
        long pulled = 0, written = 0;
        int pageNum = 0;
        Pageable pageable = PageRequest.of(pageNum, PAGE_SIZE,
                Sort.by(Sort.Order.asc("updatedAt"), Sort.Order.asc("id")));
        while (true) {
            Page<Activity> page = activities.findAll(pageable);
            if (page.isEmpty()) break;
            List<Object[]> batch = new ArrayList<>(page.getNumberOfElements());
            for (Activity a : page.getContent()) {
                batch.add(mapRow(a, snapshotDate, runAt));
            }
            int[] rc = ch.batchUpdate(SQL, batch);
            for (int n : rc) written += Math.max(n, 0);
            pulled += page.getNumberOfElements();
            if (!page.hasNext()) break;
            pageNum++;
            pageable = PageRequest.of(pageNum, PAGE_SIZE,
                    Sort.by(Sort.Order.asc("updatedAt"), Sort.Order.asc("id")));
        }
        log.info("fact_activity_progress snapshot={} pulled={} written={}", today, pulled, written);
        return new SyncReport(runAt, pulled, written, "snapshot " + today);
    }

    private static Object[] mapRow(Activity a, Date snapshotDate, Instant runAt) {
        return new Object[]{
                HandlerSupport.uuidOrEmpty(a.getId()),
                snapshotDate,
                HandlerSupport.uuidOrEmpty(a.getProjectId()),
                HandlerSupport.uuidOrEmpty(a.getWbsNodeId()),
                HandlerSupport.enumName(a.getStatus()),
                a.getPercentComplete(),
                a.getPhysicalPercentComplete(),
                a.getDurationPercentComplete(),
                a.getUnitsPercentComplete(),
                HandlerSupport.toSqlDate(a.getPlannedStartDate()),
                HandlerSupport.toSqlDate(a.getPlannedFinishDate()),
                HandlerSupport.toSqlDate(a.getEarlyStartDate()),
                HandlerSupport.toSqlDate(a.getEarlyFinishDate()),
                HandlerSupport.toSqlDate(a.getLateStartDate()),
                HandlerSupport.toSqlDate(a.getLateFinishDate()),
                HandlerSupport.toSqlDate(a.getActualStartDate()),
                HandlerSupport.toSqlDate(a.getActualFinishDate()),
                a.getOriginalDuration(),
                a.getRemainingDuration(),
                a.getAtCompletionDuration(),
                a.getTotalFloat(),
                a.getFreeFloat(),
                HandlerSupport.boolToInt(a.getIsCritical()),
                HandlerSupport.toSqlDate(a.getSuspendDate()),
                HandlerSupport.toSqlDate(a.getResumeDate()),
                HandlerSupport.uuidOrNull(a.getAssignedTo()),
                HandlerSupport.uuidOrNull(a.getResponsibleUserId()),
                HandlerSupport.toSqlTs(runAt)
        };
    }
}
