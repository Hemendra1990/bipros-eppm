package com.bipros.analytics.etl.event;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.ActivitiesBulkUpdatedEvent;
import com.bipros.common.event.ActivityCreatedEvent;
import com.bipros.common.event.ActivityUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

/**
 * Real-time refresh of {@code bipros_analytics.dim_activity}.
 *
 * <p>Three event flavours:
 * <ul>
 *   <li>{@link ActivityCreatedEvent} / {@link ActivityUpdatedEvent} — single-row upsert.</li>
 *   <li>{@link ActivitiesBulkUpdatedEvent} — emitted by sweep-style updates (P6 import,
 *       progress sweeps, status rollups). Fetches all activities in one shot and writes
 *       a single batch so we don't flood ClickHouse with 5k round-trips.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityDimensionListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final ActivityRepository activityRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivityCreated(ActivityCreatedEvent event) {
        upsertSingle("ActivityCreatedEvent", event.activityId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivityUpdated(ActivityUpdatedEvent event) {
        upsertSingle("ActivityUpdatedEvent", event.activityId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivitiesBulkUpdated(ActivitiesBulkUpdatedEvent event) {
        try {
            List<UUID> ids = event.activityIds();
            if (ids == null || ids.isEmpty()) {
                log.debug("ActivitiesBulkUpdatedEvent with no ids — skipping");
                return;
            }
            log.debug("ETL start ActivitiesBulkUpdatedEvent: project={} count={}",
                    event.projectId(), ids.size());
            List<Activity> activities = activityRepository.findAllById(ids);
            etl.upsertActivitiesBulkDimension(activities);
            log.debug("ETL done ActivitiesBulkUpdatedEvent: rows={}", activities.size());
        } catch (Exception e) {
            log.error("ETL failed for ActivitiesBulkUpdatedEvent: {}", event, e);
            deadLetter.record("activity.activities", "dim_activity", event, e);
        }
    }

    private void upsertSingle(String label, UUID activityId, Object event) {
        try {
            log.debug("ETL start {}: activity={}", label, activityId);
            Activity a = activityRepository.findById(activityId).orElse(null);
            if (a == null) {
                log.warn("Activity not found for {}: {}", label, activityId);
                return;
            }
            etl.upsertActivityDimension(a);
            log.debug("ETL done {}: activity={}", label, activityId);
        } catch (Exception e) {
            log.error("ETL failed for {}: {}", label, event, e);
            deadLetter.record("activity.activities", "dim_activity", event, e);
        }
    }
}
