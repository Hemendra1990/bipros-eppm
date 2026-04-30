package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.PermitLifecycleRecordedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermitLifecycleListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPermitLifecycle(PermitLifecycleRecordedEvent event) {
        try {
            etl.insertPermitLifecycle(
                    event.projectId(),
                    event.permitId(),
                    event.permitTypeTemplateId(),
                    event.eventType(),
                    event.occurredAt(),
                    event.actorUserId(),
                    event.riskLevel(),
                    event.permitStatus(),
                    event.payloadJson(),
                    null);
        } catch (Exception e) {
            log.error("ETL failed for PermitLifecycleRecordedEvent: {}", event, e);
            deadLetter.record("permit.permit_lifecycle_events", "fact_permit_lifecycle", event, e);
        }
    }
}
