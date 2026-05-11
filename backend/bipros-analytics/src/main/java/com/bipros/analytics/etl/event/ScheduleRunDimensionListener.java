package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.ScheduleRunRecordedEvent;
import com.bipros.scheduling.domain.model.ScheduleResult;
import com.bipros.scheduling.domain.repository.ScheduleResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Real-time refresh of {@code bipros_analytics.dim_schedule_run} after a CPM run is
 * persisted in {@code scheduling.schedule_results}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleRunDimensionListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final ScheduleResultRepository scheduleResultRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScheduleRunRecorded(ScheduleRunRecordedEvent event) {
        try {
            log.debug("ETL start ScheduleRunRecordedEvent: run={}", event.scheduleRunId());
            ScheduleResult s = scheduleResultRepository.findById(event.scheduleRunId()).orElse(null);
            if (s == null) {
                log.warn("ScheduleResult not found for event: {}", event.scheduleRunId());
                return;
            }
            etl.upsertScheduleRunDimension(s);
            log.debug("ETL done ScheduleRunRecordedEvent: run={}", event.scheduleRunId());
        } catch (Exception e) {
            log.error("ETL failed for ScheduleRunRecordedEvent: {}", event, e);
            deadLetter.record("scheduling.schedule_results", "dim_schedule_run", event, e);
        }
    }
}
