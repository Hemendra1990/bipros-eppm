package com.bipros.ai.agent.trigger;

import com.bipros.ai.agent.pipeline.AgentPipelines;
import com.bipros.common.event.ActivityExpenseRecordedEvent;
import com.bipros.common.event.BaselineCapturedEvent;
import com.bipros.common.event.DailyOutputChangedEvent;
import com.bipros.common.event.DocumentUploadedEvent;
import com.bipros.common.event.DprIssueChangedEvent;
import com.bipros.common.event.DprSubmittedEvent;
import com.bipros.common.event.GisSnapshotAnalyzedEvent;
import com.bipros.common.event.EvmRecalculatedEvent;
import com.bipros.common.event.GeneralExpenseLoggedEvent;
import com.bipros.common.event.LabourReturnSubmittedEvent;
import com.bipros.common.event.MaterialConsumptionLoggedEvent;
import com.bipros.common.event.QcTestFailedEvent;
import com.bipros.common.event.ResourceDeploymentSavedEvent;
import com.bipros.common.event.RiskAssessedEvent;
import com.bipros.common.event.RiskClosedEvent;
import com.bipros.common.event.ScheduleRunRecordedEvent;
import com.bipros.common.event.VariationOrderApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps domain events to reactive agent pipelines by coalescing a trigger for the event's project.
 * Runs AFTER_COMMIT (mirrors {@code DbsRecomputeListener}) so the source write is durable before a
 * pipeline is queued, and so a coalescer failure can never roll back the caller's transaction.
 *
 * <p>Mappings:
 * <ul>
 *   <li>Operations (DPR / output / labour / deployment / material / expense) → {@code OPERATIONS_REACTIVE}</li>
 *   <li>Schedule / baseline / EVM / VO / activity-expense → {@code SCHEDULE_REACTIVE}</li>
 *   <li>Risk / QC / DPR-issue → {@code RISK_REACTIVE}</li>
 * </ul>
 * Any event whose projectId is null is skipped with a warn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTriggerListener {

    private static final String TRIGGER_TYPE = "EVENT";

    private final AgentTriggerCoalescer coalescer;

    // ----- OPERATIONS_REACTIVE -----

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDprSubmitted(DprSubmittedEvent e) {
        trigger(AgentPipelines.OPERATIONS_REACTIVE, e.projectId(), "DprSubmittedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDailyOutputChanged(DailyOutputChangedEvent e) {
        trigger(AgentPipelines.OPERATIONS_REACTIVE, e.projectId(), "DailyOutputChangedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLabourReturnSubmitted(LabourReturnSubmittedEvent e) {
        trigger(AgentPipelines.OPERATIONS_REACTIVE, e.projectId(), "LabourReturnSubmittedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResourceDeploymentSaved(ResourceDeploymentSavedEvent e) {
        trigger(AgentPipelines.OPERATIONS_REACTIVE, e.projectId(), "ResourceDeploymentSavedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMaterialConsumptionLogged(MaterialConsumptionLoggedEvent e) {
        trigger(AgentPipelines.OPERATIONS_REACTIVE, e.projectId(), "MaterialConsumptionLoggedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGeneralExpenseLogged(GeneralExpenseLoggedEvent e) {
        trigger(AgentPipelines.OPERATIONS_REACTIVE, e.projectId(), "GeneralExpenseLoggedEvent");
    }

    // ----- SCHEDULE_REACTIVE -----

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBaselineCaptured(BaselineCapturedEvent e) {
        trigger(AgentPipelines.SCHEDULE_REACTIVE, e.projectId(), "BaselineCapturedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScheduleRunRecorded(ScheduleRunRecordedEvent e) {
        trigger(AgentPipelines.SCHEDULE_REACTIVE, e.projectId(), "ScheduleRunRecordedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEvmRecalculated(EvmRecalculatedEvent e) {
        trigger(AgentPipelines.SCHEDULE_REACTIVE, e.projectId(), "EvmRecalculatedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVariationOrderApproved(VariationOrderApprovedEvent e) {
        trigger(AgentPipelines.SCHEDULE_REACTIVE, e.projectId(), "VariationOrderApprovedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivityExpenseRecorded(ActivityExpenseRecordedEvent e) {
        trigger(AgentPipelines.SCHEDULE_REACTIVE, e.projectId(), "ActivityExpenseRecordedEvent");
    }

    // ----- RISK_REACTIVE -----

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRiskAssessed(RiskAssessedEvent e) {
        trigger(AgentPipelines.RISK_REACTIVE, e.projectId(), "RiskAssessedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRiskClosed(RiskClosedEvent e) {
        trigger(AgentPipelines.RISK_REACTIVE, e.projectId(), "RiskClosedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQcTestFailed(QcTestFailedEvent e) {
        trigger(AgentPipelines.RISK_REACTIVE, e.projectId(), "QcTestFailedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDprIssueChanged(DprIssueChangedEvent e) {
        trigger(AgentPipelines.RISK_REACTIVE, e.projectId(), "DprIssueChangedEvent");
    }

    // ----- DOCUMENT_REACTIVE / GIS_REACTIVE -----
    // fallbackExecution=true: these can be published from non-transactional or @Async paths;
    // run the handler even when there is no ambient transaction to commit.

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDocumentUploaded(DocumentUploadedEvent e) {
        trigger(AgentPipelines.DOCUMENT_REACTIVE, e.projectId(), "DocumentUploadedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGisSnapshotAnalyzed(GisSnapshotAnalyzedEvent e) {
        trigger(AgentPipelines.GIS_REACTIVE, e.projectId(), "GisSnapshotAnalyzedEvent");
    }

    // ----- shared -----

    private void trigger(String pipelineKey, UUID projectId, String triggerRef) {
        if (projectId == null) {
            log.warn("AgentTriggerListener: {} had null projectId — cannot queue {}", triggerRef, pipelineKey);
            return;
        }
        try {
            coalescer.upsert(pipelineKey, projectId, TRIGGER_TYPE, triggerRef, Instant.now());
        } catch (Exception ex) {
            // Best-effort: a queue failure must not break the (already committed) caller.
            log.warn("AgentTriggerListener: failed to queue {} for project {} from {}: {}",
                    pipelineKey, projectId, triggerRef, ex.getMessage());
        }
    }
}
