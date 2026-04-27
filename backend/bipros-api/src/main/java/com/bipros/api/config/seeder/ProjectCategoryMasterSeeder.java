package com.bipros.api.config.seeder;

import com.bipros.project.domain.model.ProjectCategoryMaster;
import com.bipros.project.domain.repository.ProjectCategoryMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the project category master data table with default MoRTH taxonomy values.
 * Runs before any project seeders so they can reference these categories.
 */
@Component
@Order(55)
@RequiredArgsConstructor
@Slf4j
public class ProjectCategoryMasterSeeder implements CommandLineRunner {

    private final ProjectCategoryMasterRepository repository;

    @Override
    @Transactional
    public void run(String... args) {
        if (repository.count() > 0) {
            log.info("Project category master data already seeded, skipping");
            return;
        }

        List<ProjectCategoryMaster> categories = List.of(
            create("HIGHWAY", "Highway", "National Highways and major arterial roads", 1),
            create("EXPRESSWAY", "Expressway", "Controlled-access high-speed corridors", 2),
            create("RURAL_ROAD", "Rural Road", "PMGSY and village connectivity roads", 3),
            create("STATE_HIGHWAY", "State Highway", "State-maintained highway corridors", 4),
            create("URBAN_ROAD", "Urban Road", "City and municipal road networks", 5),
            create("OTHER", "Other", "Miscellaneous or undefined road categories", 6)
        );

        repository.saveAll(categories);
        log.info("Seeded {} project category master records", categories.size());
    }

    private ProjectCategoryMaster create(String code, String name, String description, int sortOrder) {
        ProjectCategoryMaster m = new ProjectCategoryMaster();
        m.setCode(code);
        m.setName(name);
        m.setDescription(description);
        m.setActive(true);
        m.setSortOrder(sortOrder);
        return m;
    }
}
