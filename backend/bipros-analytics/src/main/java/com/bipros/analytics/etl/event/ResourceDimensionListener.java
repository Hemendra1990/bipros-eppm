package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.ResourceCreatedEvent;
import com.bipros.common.event.ResourceUpdatedEvent;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Real-time refresh of {@code bipros_analytics.dim_resource} on resource create/update.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceDimensionListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final ResourceRepository resourceRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResourceCreated(ResourceCreatedEvent event) {
        upsert("ResourceCreatedEvent", event.resourceId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResourceUpdated(ResourceUpdatedEvent event) {
        upsert("ResourceUpdatedEvent", event.resourceId(), event);
    }

    private void upsert(String label, UUID resourceId, Object event) {
        try {
            log.debug("ETL start {}: resource={}", label, resourceId);
            Resource r = resourceRepository.findById(resourceId).orElse(null);
            if (r == null) {
                log.warn("Resource not found for {}: {}", label, resourceId);
                return;
            }
            etl.upsertResourceDimension(r);
            log.debug("ETL done {}: resource={}", label, resourceId);
        } catch (Exception e) {
            log.error("ETL failed for {}: {}", label, event, e);
            deadLetter.record("resource.resources", "dim_resource", event, e);
        }
    }
}
