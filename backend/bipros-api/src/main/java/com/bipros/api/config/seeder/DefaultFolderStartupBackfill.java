package com.bipros.api.config.seeder;

import com.bipros.document.application.listener.DefaultFolderSeeder;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Demo seeders (NhaiRoadProjectSeeder, IcpmsPhaseASeeder, IoclPanipatSeeder)
 * implement CommandLineRunner and call projectRepository.save(project)
 * directly, bypassing ProjectService.createProject() — so ProjectCreatedEvent
 * never fires for them. Spring Boot guarantees ApplicationReadyEvent fires
 * after every CommandLineRunner has completed; we hook there and ensure each
 * existing project has the default folder set, calling the same idempotent
 * seed method used by the live event listener.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultFolderStartupBackfill {

    private final ProjectRepository projectRepository;
    private final DefaultFolderSeeder defaultFolderSeeder;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("DefaultFolderStartupBackfill: scanning projects for missing default folders");
        int processed = 0;
        for (var project : projectRepository.findAll()) {
            try {
                defaultFolderSeeder.seedDefaultsIfMissing(project.getId());
                processed++;
            } catch (RuntimeException e) {
                log.warn("DefaultFolderStartupBackfill failed for project {}: {}",
                    project.getId(), e.getMessage(), e);
            }
        }
        log.info("DefaultFolderStartupBackfill: processed {} projects", processed);
    }
}
