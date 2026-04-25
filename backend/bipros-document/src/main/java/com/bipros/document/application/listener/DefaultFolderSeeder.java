package com.bipros.document.application.listener;

import com.bipros.common.event.ProjectCreatedEvent;
import com.bipros.document.domain.model.DocumentCategory;
import com.bipros.document.domain.model.DocumentFolder;
import com.bipros.document.domain.repository.DocumentFolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Auto-seeds 7 standard root folders for a project. Two entry points:
 *  - @TransactionalEventListener for normal project creation via ProjectService
 *  - public seedDefaultsIfMissing(projectId) for the bipros-api startup backfill
 *    that covers demo seeders bypassing ProjectService.
 *
 * Idempotent: skips when the project already has root folders.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultFolderSeeder {

    private final DocumentFolderRepository folderRepository;

    private record FolderTemplate(String name, String code, DocumentCategory category) {}

    private static final List<FolderTemplate> DEFAULTS = List.of(
        new FolderTemplate("Drawings",       "DRW",  DocumentCategory.DRAWING),
        new FolderTemplate("Specifications", "SPEC", DocumentCategory.SPECIFICATION),
        new FolderTemplate("Contracts",      "CON",  DocumentCategory.CONTRACT),
        new FolderTemplate("Approvals",      "APR",  DocumentCategory.APPROVAL),
        new FolderTemplate("Correspondence", "COR",  DocumentCategory.CORRESPONDENCE),
        new FolderTemplate("As-Built",       "AB",   DocumentCategory.AS_BUILT),
        new FolderTemplate("General",        "GEN",  DocumentCategory.GENERAL)
    );

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProjectCreated(ProjectCreatedEvent event) {
        try {
            seedDefaultsIfMissing(event.projectId());
        } catch (RuntimeException e) {
            log.warn("DefaultFolderSeeder failed for project {}: {}", event.projectId(), e.getMessage(), e);
        }
    }

    /**
     * Idempotent: returns immediately if any root folder exists for the project.
     * Otherwise creates the 7 canonical folders in a single saveAll batch.
     * Public so {@code DefaultFolderStartupBackfill} (in bipros-api) can call it.
     */
    @Transactional
    public void seedDefaultsIfMissing(UUID projectId) {
        List<DocumentFolder> existing =
            folderRepository.findByProjectIdAndParentIdIsNullOrderBySortOrder(projectId);
        if (!existing.isEmpty()) {
            log.info("DefaultFolderSeeder: project {} already has {} root folders, skipping",
                projectId, existing.size());
            return;
        }

        List<DocumentFolder> toCreate = new ArrayList<>(DEFAULTS.size());
        for (int i = 0; i < DEFAULTS.size(); i++) {
            FolderTemplate t = DEFAULTS.get(i);
            DocumentFolder f = new DocumentFolder();
            f.setProjectId(projectId);
            f.setParentId(null);
            f.setName(t.name());
            f.setCode(t.code());
            f.setCategory(t.category());
            f.setSortOrder(i);
            toCreate.add(f);
        }
        folderRepository.saveAll(toCreate);
        log.info("DefaultFolderSeeder created {} root folders for project {}",
            toCreate.size(), projectId);
    }
}
