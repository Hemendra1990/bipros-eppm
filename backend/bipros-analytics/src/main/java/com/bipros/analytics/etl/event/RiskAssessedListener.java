package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.analytics.etl.dto.RiskSnapshotRow;
import com.bipros.common.event.RiskAssessedEvent;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.repository.RiskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskAssessedListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final RiskRepository riskRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRiskAssessed(RiskAssessedEvent event) {
        try {
            Risk risk = riskRepository.findById(event.riskId()).orElse(null);
            if (risk == null) {
                log.warn("Risk not found for event: {}", event);
                return;
            }
            etl.insertRiskSnapshotDaily(toSnapshot(risk, LocalDate.now()));
            log.debug("ETL processed RiskAssessedEvent: project={} risk={}",
                    event.projectId(), event.riskId());
        } catch (Exception e) {
            log.error("ETL failed for RiskAssessedEvent: {}", event, e);
            deadLetter.record("risk.risks", "fact_risk_snapshot_daily", event, e);
        }
    }

    static RiskSnapshotRow toSnapshot(Risk risk, LocalDate date) {
        Float probability = risk.getProbability() != null
                ? (float) risk.getProbability().getValue() : null;
        Float postProbability = risk.getPostResponseProbability() != null
                ? (float) risk.getPostResponseProbability().getValue() : null;
        return RiskSnapshotRow.builder()
                .projectId(risk.getProjectId())
                .riskId(risk.getId())
                .date(date)
                .probability(probability)
                .impactCost(risk.getCostImpact())
                .impactDays(risk.getScheduleImpactDays())
                .rag(risk.getRag() != null ? risk.getRag().name() : null)
                .status(risk.getStatus() != null ? risk.getStatus().name() : null)
                .monteCarloP50(null)
                .monteCarloP80(null)
                .monteCarloP95(null)
                .riskScore(risk.getRiskScore())
                .residualRiskScore(risk.getResidualRiskScore())
                .riskType(risk.getRiskType() != null ? risk.getRiskType().name() : "THREAT")
                .ownerId(risk.getOwnerId())
                .categoryId(risk.getCategory() != null ? risk.getCategory().getId() : null)
                .postResponseProbability(postProbability)
                .postResponseImpactCost(risk.getPostResponseImpactCost())
                .postResponseImpactSchedule(risk.getPostResponseImpactSchedule())
                .preResponseExposureCost(risk.getPreResponseExposureCost())
                .postResponseExposureCost(risk.getPostResponseExposureCost())
                .exposureStartDate(risk.getExposureStartDate())
                .exposureFinishDate(risk.getExposureFinishDate())
                .responseType(risk.getResponseType() != null ? risk.getResponseType().name() : null)
                .trend(risk.getTrend() != null ? risk.getTrend().name() : null)
                .identifiedDate(risk.getIdentifiedDate())
                .identifiedById(risk.getIdentifiedById())
                .build();
    }
}
