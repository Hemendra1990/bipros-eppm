package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.infrastructure.repository.BaselineRepository;
import com.bipros.common.event.BaselineCapturedEvent;
import com.bipros.common.event.BaselineDeactivatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Real-time refresh of {@code bipros_analytics.dim_baseline}.
 *
 * <p>{@link BaselineCapturedEvent} writes the active row.
 * {@link BaselineDeactivatedEvent} writes an {@code is_active=0} row with a strictly
 * newer _version, so the ReplacingMergeTree merge converges to the deactivated state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaselineDimensionListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final BaselineRepository baselineRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBaselineCaptured(BaselineCapturedEvent event) {
        try {
            log.debug("ETL start BaselineCapturedEvent: baseline={}", event.baselineId());
            Baseline b = baselineRepository.findById(event.baselineId()).orElse(null);
            if (b == null) {
                log.warn("Baseline not found for BaselineCapturedEvent: {}", event.baselineId());
                return;
            }
            etl.upsertBaselineDimension(b, true);
            log.debug("ETL done BaselineCapturedEvent: baseline={}", event.baselineId());
        } catch (Exception e) {
            log.error("ETL failed for BaselineCapturedEvent: {}", event, e);
            deadLetter.record("baseline.baselines", "dim_baseline", event, e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBaselineDeactivated(BaselineDeactivatedEvent event) {
        try {
            log.debug("ETL start BaselineDeactivatedEvent: baseline={}", event.baselineId());
            Baseline b = baselineRepository.findById(event.baselineId()).orElse(null);
            if (b == null) {
                log.warn("Baseline not found for BaselineDeactivatedEvent: {}", event.baselineId());
                return;
            }
            etl.upsertBaselineDimension(b, false);
            log.debug("ETL done BaselineDeactivatedEvent: baseline={}", event.baselineId());
        } catch (Exception e) {
            log.error("ETL failed for BaselineDeactivatedEvent: {}", event, e);
            deadLetter.record("baseline.baselines", "dim_baseline", event, e);
        }
    }
}
