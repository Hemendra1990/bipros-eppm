package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.VariationOrderApprovedEvent;
import com.bipros.contract.domain.model.VariationOrder;
import com.bipros.contract.domain.repository.VariationOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Real-time refresh of {@code bipros_analytics.dim_contract} when a Variation Order is
 * approved. The VO entity itself does not carry a project id (only contract id), so we
 * lift the projectId off the event payload — the publisher already resolved it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractDimensionListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final VariationOrderRepository variationOrderRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVariationOrderApproved(VariationOrderApprovedEvent event) {
        try {
            log.debug("ETL start VariationOrderApprovedEvent: vo={} project={}",
                    event.voId(), event.projectId());
            VariationOrder vo = variationOrderRepository.findById(event.voId()).orElse(null);
            if (vo == null) {
                log.warn("VariationOrder not found for event: {}", event.voId());
                return;
            }
            etl.upsertContractDimension(vo, event.projectId());
            log.debug("ETL done VariationOrderApprovedEvent: vo={}", event.voId());
        } catch (Exception e) {
            log.error("ETL failed for VariationOrderApprovedEvent: {}", event, e);
            deadLetter.record("contract.variation_orders", "dim_contract", event, e);
        }
    }
}
