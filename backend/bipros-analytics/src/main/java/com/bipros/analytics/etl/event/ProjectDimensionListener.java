package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.ProjectCreatedEvent;
import com.bipros.common.event.ProjectUpdatedEvent;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Real-time refresh of {@code bipros_analytics.dim_project} on project create/update.
 * Fires AFTER_COMMIT so the new/updated row is already visible to the read transaction
 * we open here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDimensionListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final ProjectRepository projectRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectCreated(ProjectCreatedEvent event) {
        upsert("ProjectCreatedEvent", event.projectId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectUpdated(ProjectUpdatedEvent event) {
        upsert("ProjectUpdatedEvent", event.projectId(), event);
    }

    private void upsert(String label, UUID projectId, Object event) {
        try {
            log.debug("ETL start {}: project={}", label, projectId);
            Project p = projectRepository.findById(projectId).orElse(null);
            if (p == null) {
                log.warn("Project not found for {}: {}", label, projectId);
                return;
            }
            etl.upsertProjectDimension(p);
            log.debug("ETL done {}: project={}", label, projectId);
        } catch (Exception e) {
            log.error("ETL failed for {}: {}", label, event, e);
            deadLetter.record("project.projects", "dim_project", event, e);
        }
    }
}
