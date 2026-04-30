package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.LabourReturnSubmittedEvent;
import com.bipros.resource.domain.model.LabourReturn;
import com.bipros.resource.domain.repository.LabourReturnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LabourReturnSubmittedListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final LabourReturnRepository labourReturnRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLabourReturnSubmitted(LabourReturnSubmittedEvent event) {
        try {
            LabourReturn lr = labourReturnRepository.findById(event.labourReturnId()).orElse(null);
            if (lr == null) {
                log.warn("LabourReturn not found for event: {}", event);
                return;
            }
            Float manDays = lr.getManDays() != null ? lr.getManDays().floatValue() : 0f;
            etl.insertLabourDaily(
                    lr.getProjectId(),
                    lr.getId(),
                    null,
                    null,
                    lr.getSkillCategory() != null ? lr.getSkillCategory().name() : null,
                    lr.getContractorName(),
                    null,
                    lr.getWbsNodeId(),
                    lr.getSiteLocation(),
                    lr.getReturnDate(),
                    lr.getHeadCount(),
                    manDays,
                    null,
                    null,
                    null,
                    "LABOUR_RETURN");
        } catch (Exception e) {
            log.error("ETL failed for LabourReturnSubmittedEvent: {}", event, e);
            deadLetter.record("resource.labour_returns", "fact_labour_daily", event, e);
        }
    }
}
