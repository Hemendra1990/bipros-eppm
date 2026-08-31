package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.WbsCreatedEvent;
import com.bipros.common.event.WbsUpdatedEvent;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.WbsNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Real-time refresh of {@code bipros_analytics.dim_wbs} on WBS-node create/update.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WbsDimensionListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final WbsNodeRepository wbsNodeRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWbsCreated(WbsCreatedEvent event) {
        upsert("WbsCreatedEvent", event.wbsId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWbsUpdated(WbsUpdatedEvent event) {
        upsert("WbsUpdatedEvent", event.wbsId(), event);
    }

    private void upsert(String label, UUID wbsId, Object event) {
        try {
            log.debug("ETL start {}: wbs={}", label, wbsId);
            WbsNode w = wbsNodeRepository.findById(wbsId).orElse(null);
            if (w == null) {
                log.warn("WbsNode not found for {}: {}", label, wbsId);
                return;
            }
            etl.upsertWbsDimension(w);
            log.debug("ETL done {}: wbs={}", label, wbsId);
        } catch (Exception e) {
            log.error("ETL failed for {}: {}", label, event, e);
            deadLetter.record("project.wbs_nodes", "dim_wbs", event, e);
        }
    }
}
