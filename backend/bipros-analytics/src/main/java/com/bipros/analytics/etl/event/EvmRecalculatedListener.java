package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.analytics.etl.batch.EvmDailyInterpolator;
import com.bipros.common.event.EvmRecalculatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class EvmRecalculatedListener {

    private final DeadLetterHandler deadLetter;
    private final EvmDailyInterpolator interpolator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEvmRecalculated(EvmRecalculatedEvent event) {
        try {
            interpolator.interpolateProject(event.projectId());
            log.debug("ETL processed EvmRecalculatedEvent: project={} calc={}",
                    event.projectId(), event.evmCalculationId());
        } catch (Exception e) {
            log.error("ETL failed for EvmRecalculatedEvent: {}", event, e);
            deadLetter.record("evm.evm_calculations", "fact_evm_daily", event, e);
        }
    }
}
