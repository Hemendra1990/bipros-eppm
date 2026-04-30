package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.analytics.etl.dto.RiskSnapshotRow;
import com.bipros.common.event.RiskClosedEvent;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.repository.RiskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskClosedListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final RiskRepository riskRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRiskClosed(RiskClosedEvent event) {
        try {
            LocalDate closedDate = event.closedAt() != null
                    ? event.closedAt().atZone(ZoneId.systemDefault()).toLocalDate()
                    : LocalDate.now();
            Risk risk = riskRepository.findById(event.riskId()).orElse(null);
            RiskSnapshotRow row;
            if (risk != null) {
                row = RiskAssessedListener.toSnapshot(risk, closedDate);
            } else {
                row = RiskSnapshotRow.builder()
                        .projectId(event.projectId())
                        .riskId(event.riskId())
                        .date(closedDate)
                        .status("CLOSED")
                        .riskType("THREAT")
                        .build();
            }
            etl.insertRiskSnapshotDaily(row);
            log.debug("ETL processed RiskClosedEvent: project={} risk={}",
                    event.projectId(), event.riskId());
        } catch (Exception e) {
            log.error("ETL failed for RiskClosedEvent: {}", event, e);
            deadLetter.record("risk.risks", "fact_risk_snapshot_daily", event, e);
        }
    }
}
