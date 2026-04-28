package com.bipros.analytics.etl.handler;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.analytics.etl.SyncHandler;
import com.bipros.analytics.etl.SyncReport;
import com.bipros.analytics.etl.support.HandlerSupport;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineActivity;
import com.bipros.baseline.infrastructure.repository.BaselineActivityRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily snapshot of (baseline, current activity) variance. For every active baseline, joins each
 * baseline activity with the current state of the same activity to compute start / finish /
 * percent-complete deltas. One row per (activity, baseline, snapshot_date).
 *
 * <p>Source data is not temporally addressable, so the cadence matches
 * {@code fact_activity_progress}: 1 day. Re-runs the same day collapse via
 * {@code ReplacingMergeTree(updated_at)}.
 */
@Component
@Slf4j
public class FactBaselineVarianceSyncHandler implements SyncHandler {

    private static final String SQL =
            "INSERT INTO fact_baseline_variance (activity_id, baseline_id, snapshot_date, " +
            "project_id, baseline_early_start, baseline_early_finish, baseline_late_start, " +
            "baseline_late_finish, baseline_planned_cost, baseline_percent_complete, " +
            "current_planned_start, current_planned_finish, current_actual_start, " +
            "current_actual_finish, current_percent_complete, start_variance_days, " +
            "finish_variance_days, duration_variance, percent_complete_variance, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate ch;
    private final BaselineRepository baselines;
    private final BaselineActivityRepository baselineActivities;
    private final ActivityRepository activities;

    public FactBaselineVarianceSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate ch,
                                           BaselineRepository baselines,
                                           BaselineActivityRepository baselineActivities,
                                           ActivityRepository activities) {
        this.ch = ch;
        this.baselines = baselines;
        this.baselineActivities = baselineActivities;
        this.activities = activities;
    }

    @Override public String tableName() { return "fact_baseline_variance"; }
    @Override public Duration cadence() { return Duration.ofDays(1); }

    @Override
    @Transactional(readOnly = true)
    public SyncReport sync(Instant since) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Date snapshotDate = Date.valueOf(today);
        Instant runAt = Instant.now();
        long pulled = 0, written = 0;

        List<Baseline> active = baselines.findByIsActiveTrue();
        for (Baseline b : active) {
            List<BaselineActivity> bActs = baselineActivities.findByBaselineId(b.getId());
            if (bActs.isEmpty()) continue;
            Map<UUID, Activity> currentByActivity = new HashMap<>();
            List<UUID> activityIds = bActs.stream().map(BaselineActivity::getActivityId).toList();
            for (Activity a : activities.findByIdIn(activityIds)) {
                currentByActivity.put(a.getId(), a);
            }

            List<Object[]> batch = new ArrayList<>(bActs.size());
            for (BaselineActivity ba : bActs) {
                Activity cur = currentByActivity.get(ba.getActivityId());
                if (cur == null) continue;
                batch.add(rowFor(ba, cur, b, snapshotDate, runAt));
            }
            if (!batch.isEmpty()) {
                int[] rc = ch.batchUpdate(SQL, batch);
                for (int n : rc) written += Math.max(n, 0);
                pulled += batch.size();
            }
        }
        log.info("fact_baseline_variance snapshot={} baselines={} pulled={} written={}",
                today, active.size(), pulled, written);
        return new SyncReport(runAt, pulled, written, "snapshot " + today);
    }

    private static Object[] rowFor(BaselineActivity ba, Activity cur, Baseline b, Date snapshotDate, Instant runAt) {
        Integer startVar = daysBetween(ba.getEarlyStart(), cur.getPlannedStartDate());
        Integer finishVar = daysBetween(ba.getEarlyFinish(), cur.getPlannedFinishDate());
        Double durationVar = doubleDiff(cur.getOriginalDuration(), ba.getOriginalDuration());
        Double pctVar = doubleDiff(cur.getPercentComplete(), ba.getPercentComplete());
        return new Object[]{
                HandlerSupport.uuidOrEmpty(ba.getActivityId()),
                HandlerSupport.uuidOrEmpty(b.getId()),
                snapshotDate,
                HandlerSupport.uuidOrEmpty(b.getProjectId()),
                HandlerSupport.toSqlDate(ba.getEarlyStart()),
                HandlerSupport.toSqlDate(ba.getEarlyFinish()),
                HandlerSupport.toSqlDate(ba.getLateStart()),
                HandlerSupport.toSqlDate(ba.getLateFinish()),
                ba.getPlannedCost(),
                ba.getPercentComplete(),
                HandlerSupport.toSqlDate(cur.getPlannedStartDate()),
                HandlerSupport.toSqlDate(cur.getPlannedFinishDate()),
                HandlerSupport.toSqlDate(cur.getActualStartDate()),
                HandlerSupport.toSqlDate(cur.getActualFinishDate()),
                cur.getPercentComplete(),
                startVar,
                finishVar,
                durationVar,
                pctVar,
                HandlerSupport.toSqlTs(runAt)
        };
    }

    private static Integer daysBetween(LocalDate baseline, LocalDate current) {
        if (baseline == null || current == null) return null;
        return (int) ChronoUnit.DAYS.between(baseline, current);
    }

    private static Double doubleDiff(Double a, Double b) {
        if (a == null || b == null) return null;
        return a - b;
    }
}
