package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.LabourDeploymentChangedEvent;
import com.bipros.resource.domain.model.LabourDesignation;
import com.bipros.resource.domain.model.ProjectLabourDeployment;
import com.bipros.resource.domain.repository.LabourDesignationRepository;
import com.bipros.resource.domain.repository.ProjectLabourDeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Writes a today-grain {@code fact_labour_daily} row whenever a project labour deployment
 * is created or updated. The nightly {@code DimensionSyncJob.syncLabourDeploymentSnapshot}
 * fills in any gaps between events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LabourDeploymentChangedListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final ProjectLabourDeploymentRepository deploymentRepository;
    private final LabourDesignationRepository designationRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeploymentChanged(LabourDeploymentChangedEvent event) {
        if (event.changeType() == LabourDeploymentChangedEvent.ChangeType.DELETED) {
            return;
        }
        try {
            ProjectLabourDeployment dep = deploymentRepository.findById(event.deploymentId()).orElse(null);
            if (dep == null) {
                return;
            }
            LabourDesignation d = designationRepository.findById(dep.getDesignationId()).orElse(null);
            BigDecimal rate = dep.getActualDailyRate() != null
                    ? dep.getActualDailyRate()
                    : (d != null ? d.getDefaultDailyRate() : null);
            BigDecimal cost = (rate != null && dep.getWorkerCount() != null)
                    ? rate.multiply(BigDecimal.valueOf(dep.getWorkerCount()))
                    : null;
            String skillCategory = d != null && d.getCategory() != null ? d.getCategory().name() : null;

            etl.insertLabourDaily(
                    dep.getProjectId(),
                    null,
                    dep.getId(),
                    dep.getDesignationId(),
                    skillCategory,
                    d != null ? d.getDesignation() : "",
                    null,
                    null,
                    null,
                    LocalDate.now(),
                    null,
                    null,
                    dep.getWorkerCount(),
                    rate,
                    cost,
                    "DEPLOYMENT_SNAPSHOT");
        } catch (Exception e) {
            log.error("ETL failed for LabourDeploymentChangedEvent: {}", event, e);
            deadLetter.record("resource.project_labour_deployments", "fact_labour_daily", event, e);
        }
    }
}
