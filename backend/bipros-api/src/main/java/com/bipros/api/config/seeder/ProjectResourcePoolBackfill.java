package com.bipros.api.config.seeder;

import com.bipros.resource.domain.model.ProjectResource;
import com.bipros.resource.domain.repository.ProjectResourceRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(960)
public class ProjectResourcePoolBackfill implements CommandLineRunner {

    private final ProjectResourceRepository projectResourceRepository;
    private final ResourceAssignmentRepository resourceAssignmentRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (projectResourceRepository.count() > 0) {
            log.info("ProjectResourcePoolBackfill: project_resources table already has data, skipping");
            return;
        }

        log.info("ProjectResourcePoolBackfill: backfilling project_resources from resource_assignments");

        List<Object[]> pairs = resourceAssignmentRepository.findDistinctProjectResourcePairs();

        Set<String> existing = projectResourceRepository.findAll().stream()
                .map(pr -> pr.getProjectId() + ":" + pr.getResourceId())
                .collect(Collectors.toSet());

        List<ProjectResource> toInsert = pairs.stream()
                .map(row -> {
                    UUID projectId = (UUID) row[0];
                    UUID resourceId = (UUID) row[1];
                    return ProjectResource.builder()
                            .projectId(projectId)
                            .resourceId(resourceId)
                            .build();
                })
                .filter(pr -> !existing.contains(pr.getProjectId() + ":" + pr.getResourceId()))
                .toList();

        projectResourceRepository.saveAll(toInsert);
        log.info("ProjectResourcePoolBackfill: inserted {} pool entries", toInsert.size());
    }
}
