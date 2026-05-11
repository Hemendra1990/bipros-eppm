package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.CostAccountCreatedEvent;
import com.bipros.common.event.CostAccountUpdatedEvent;
import com.bipros.cost.domain.entity.CostAccount;
import com.bipros.cost.domain.repository.CostAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Real-time refresh of {@code bipros_analytics.dim_cost_account} on cost-account
 * create/update.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CostAccountDimensionListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final CostAccountRepository costAccountRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCostAccountCreated(CostAccountCreatedEvent event) {
        upsert("CostAccountCreatedEvent", event.costAccountId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCostAccountUpdated(CostAccountUpdatedEvent event) {
        upsert("CostAccountUpdatedEvent", event.costAccountId(), event);
    }

    private void upsert(String label, UUID costAccountId, Object event) {
        try {
            log.debug("ETL start {}: costAccount={}", label, costAccountId);
            CostAccount ca = costAccountRepository.findById(costAccountId).orElse(null);
            if (ca == null) {
                log.warn("CostAccount not found for {}: {}", label, costAccountId);
                return;
            }
            etl.upsertCostAccountDimension(ca);
            log.debug("ETL done {}: costAccount={}", label, costAccountId);
        } catch (Exception e) {
            log.error("ETL failed for {}: {}", label, event, e);
            deadLetter.record("cost.cost_accounts", "dim_cost_account", event, e);
        }
    }
}
